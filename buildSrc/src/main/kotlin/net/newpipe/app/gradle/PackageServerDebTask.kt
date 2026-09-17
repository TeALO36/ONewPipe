/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Builds a self-contained Debian package for headless Linux hosts:
 *
 *   onewpipe-server_<version>_<arch>.deb
 *
 * The package ships the fat jar together with a jlink'd Java runtime so no
 * system Java is required, installs a systemd unit that starts on boot
 * (`systemctl enable --now` in the postinst script) and exposes the usual
 * environment knobs (PORT, HOST, DATA_DIR, JWT_SECRET) through
 * /etc/default/onewpipe-server, which is registered as a conffile.
 *
 * Requires dpkg-deb (any Debian/Ubuntu build host, including CI runners).
 * The class lives in buildSrc: task classes declared inside a .gradle.kts
 * script are compiled as inner classes and cannot be instantiated by Gradle.
 */
abstract class PackageServerDebTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
    private val archiveOperations: ArchiveOperations
) : DefaultTask() {

    @get:InputFile
    val serverJar: RegularFileProperty = project.objects.fileProperty()

    @get:InputDirectory
    val debAssets: DirectoryProperty = project.objects.directoryProperty()

    @get:Internal
    val readmeFile: RegularFileProperty = project.objects.fileProperty()

    @get:Internal
    val jdkHome: DirectoryProperty = project.objects.directoryProperty()

    @get:Input
    val packageVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val debArch: Property<String> = project.objects.property(String::class.java)

    @get:OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @get:Internal
    val stagingDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Internal
    val runtimeDir: DirectoryProperty = project.objects.directoryProperty()

    private fun run(vararg args: String) {
        execOperations.exec {
            commandLine(*args)
        }.assertNormalExitValue()
    }

    private fun jdkTool(name: String): String {
        val suffix = if (System.getProperty("os.name").lowercase().contains("win")) ".exe" else ""
        return jdkHome.get().file("bin/$name$suffix").asFile.absolutePath
    }

    @TaskAction
    fun buildDeb() {
        val staging = stagingDir.get().asFile
        val runtime = runtimeDir.get().asFile
        fileSystemOperations.delete { delete(staging, runtime) }
        staging.mkdirs()

        val jarFile = serverJar.get().asFile

        // 1. Derive the required JDK modules from the fat jar, then add a
        // safety margin for reflective/NIO access that jdeps cannot see.
        // jdeps is fed an exploded copy without module-info.class: descriptors
        // pulled from the jar would make jdeps try to resolve those modules
        // (e.g. org.mozilla.rhino) and abort with a FindException.
        val scanDir = File(temporaryDir, "jdeps-scan")
        fileSystemOperations.delete { delete(scanDir) }
        fileSystemOperations.copy {
            from(archiveOperations.zipTree(jarFile)) {
                exclude("**/module-info.class")
            }
            into(scanDir)
        }
        val jdepsOutput = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(
                jdkTool("jdeps"),
                "--multi-release", "21",
                "--print-module-deps",
                "--ignore-missing-deps",
                scanDir.absolutePath
            )
            standardOutput = jdepsOutput
        }
        val detected = jdepsOutput.toString().trim().lineSequence()
            .filter { it.isNotBlank() && !it.contains("missing dependencies") }
            .lastOrNull()?.trim().orEmpty()
        val modules = (detected.split(",").map(String::trim).filter(String::isNotEmpty).toSet() + setOf(
            "java.logging", "java.management", "java.naming", "jdk.unsupported", "jdk.crypto.ec"
        )).joinToString(",")

        // 2. Minimal self-contained Java runtime (must come from the same
        // JDK major version the server is compiled against).
        run(
            jdkTool("jlink"),
            "--add-modules", modules,
            "--module-path", jdkHome.get().file("jmods").asFile.absolutePath,
            "--output", runtime.absolutePath,
            "--strip-debug", "--no-header-files", "--no-man-pages",
            "--compress", "zip-6"
        )

        // 3. Filesystem layout of the package.
        val libDir = staging.resolve("usr/lib/onewpipe-server")
        val systemDir = staging.resolve("lib/systemd/system")
        val etcDir = staging.resolve("etc/default")
        val docDir = staging.resolve("usr/share/doc/onewpipe-server")
        val debDir = staging.resolve("DEBIAN")
        libDir.mkdirs(); systemDir.mkdirs(); etcDir.mkdirs(); docDir.mkdirs(); debDir.mkdirs()

        fileSystemOperations.copy {
            from(jarFile)
            into(libDir)
            rename { "onewpipe-server.jar" }
        }
        fileSystemOperations.copy {
            from(runtime)
            into(libDir.resolve("runtime"))
        }
        // 644 regardless of build-host checkout modes: dpkg installs the files
        // with these permissions and systemd refuses executable unit files.
        fileSystemOperations.copy {
            from(debAssets.get().file("onewpipe-server.service"))
            into(systemDir)
        }
        fileSystemOperations.copy {
            from(debAssets.get().file("onewpipe-server.env"))
            into(etcDir)
            rename { "onewpipe-server" }
        }
        fileSystemOperations.copy {
            from(debAssets.get().file("copyright"))
            into(docDir)
        }
        if (readmeFile.get().asFile.isFile) {
            fileSystemOperations.copy {
                from(readmeFile)
                into(docDir)
            }
        }
        for (regular in listOf(
            systemDir.resolve("onewpipe-server.service"),
            etcDir.resolve("onewpipe-server"),
            docDir.resolve("copyright"),
            docDir.resolve("README.md")
        )) {
            if (regular.isFile) regular.setExecutable(false, false)
        }

        val version = packageVersion.get()
        debDir.resolve("control").writeText(
            "Package: fr.arthonetwork.onewpipe-server\n" +
                "Version: $version\n" +
                "Section: net\n" +
                "Priority: optional\n" +
                "Architecture: ${debArch.get()}\n" +
                "Depends: systemd\n" +
                "Installed-Size: ${(staging.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / 1024)}\n" +
                "Maintainer: ONewPipe <contact@arthonetwork.fr>\n" +
                "Homepage: https://github.com/ArthonNetwork/ONewPipe\n" +
                "Description: ONewPipe self-hosted server (headless)\n" +
                " Account API, watch-position synchronization and web interface for the\n" +
                " ONewPipe apps, served from a single origin. Ships its own Java runtime,\n" +
                " runs as a hardened systemd service and starts automatically at boot.\n"
        )
        for (script in listOf("postinst", "prerm", "postrm")) {
            val target = debDir.resolve(script)
            fileSystemOperations.copy {
                from(debAssets.get().file(script))
                into(debDir)
            }
            target.setExecutable(true, false)
        }
        fileSystemOperations.copy {
            from(debAssets.get().file("conffiles"))
            into(debDir)
        }

        // 4. Build the .deb (dpkg-deb handles the ar archive and control metadata).
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        run(
            "dpkg-deb", "--root-owner-group", "--build",
            staging.absolutePath,
            output.absolutePath
        )
    }
}
