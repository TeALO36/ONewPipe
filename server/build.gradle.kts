/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import net.newpipe.app.gradle.PackageServerDebTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlinx.serialization)
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

application {
    mainClass.set("fr.arthonetwork.onewpipe.server.MainKt")
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.newpipe.extractor)
    implementation(libs.squareup.okhttp)
}

// Convenience fat jar for simple deployments/CI artifacts.
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("onewpipe-server")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class")
    }
}

tasks.named("build") {
    dependsOn("fatJar")
}

// The web interface (webapp/, Vite + React) is built with npm and shipped in
// the jar under "webapp". Pass -PskipWebapp to build the server without Node:
// an existing webapp/dist (built elsewhere, as in the Docker image) is still shipped.
val webappDir = rootProject.layout.projectDirectory.dir("webapp")
tasks.named<ProcessResources>("processResources") {
    from(webappDir.dir("dist")) {
        into("webapp")
    }
}
// Read once at configuration time: the configuration cache forbids using project in task actions.
val skipWebapp = providers.gradleProperty("skipWebapp").isPresent
val isWindows = System.getProperty("os.name").lowercase().contains("windows")
fun npm(vararg args: String): List<String> =
    if (isWindows) listOf("cmd", "/c", "npm", *args) else listOf("npm", *args)

// Decided at configuration time: task actions must not reference the build
// script, or the configuration cache cannot store them.
if (!skipWebapp) {
    // `npm ci` deletes node_modules first, which fails on Windows while a Vite
    // dev server holds files there. Install only when no complete install exists
    // (npm writes node_modules/.package-lock.json once an install finishes).
    val installWebapp = if (!webappDir.file("node_modules/.package-lock.json").asFile.exists()) {
        tasks.register<Exec>("installWebapp") {
            description = "Installs the web interface dependencies."
            workingDir = webappDir.asFile
            commandLine(npm("ci", "--no-audit", "--no-fund"))
        }
    } else {
        null
    }

    val buildWebapp = tasks.register<Exec>("buildWebapp") {
        description = "Builds the web interface served at /."
        installWebapp?.let { dependsOn(it) }
        workingDir = webappDir.asFile
        commandLine(npm("run", "build"))
        inputs.dir(webappDir.dir("src"))
        inputs.dir(webappDir.dir("public"))
        inputs.files(
            webappDir.file("index.html"),
            webappDir.file("package.json"),
            webappDir.file("vite.config.ts"),
            webappDir.file("tsconfig.app.json")
        )
        outputs.dir(webappDir.dir("dist"))
    }

    tasks.named<ProcessResources>("processResources") {
        dependsOn(buildWebapp)
    }
}

// Version and architecture of the headless server .deb produced by
// :server:packageServerDeb. Override with -DversionNameOverride=1.4.0 and
// -PdebArch=amd64|arm64 (arch defaults to the current machine).
val serverDebVersion: String = (
        System.getProperty("versionNameOverride")
            ?: NEWPIPE_VERSION_NAME
    ).replace(Regex("[^0-9A-Za-z.+~]"), "")
    .ifEmpty { NEWPIPE_VERSION_NAME }
    .let { if (it.firstOrNull()?.isLetter() == true) "0.$it" else it }

val serverDebArch: String = findProperty("debArch")?.toString()
    ?: when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        else -> "amd64"
    }

tasks.register<PackageServerDebTask>("packageServerDeb") {
    group = "distribution"
    description = "Builds the headless Linux server .deb (jar + jlink runtime + systemd service)"
    dependsOn(tasks.named("fatJar"))
    serverJar.set(layout.buildDirectory.file("libs/onewpipe-server-all.jar"))
    debAssets.set(layout.projectDirectory.dir("deb"))
    readmeFile.set(rootProject.layout.projectDirectory.file("server/README.md"))
    // Inside this configuration action `extensions` refers to the task's own
    // extension container, hence the explicit `project.extensions` lookup.
    jdkHome.set(project.extensions.getByType(JavaToolchainService::class.java)
        .compilerFor { languageVersion = JavaLanguageVersion.of(21) }
        .map { it.metadata.installationPath })
    packageVersion.set(serverDebVersion)
    debArch.set(serverDebArch)
    stagingDir.set(layout.buildDirectory.dir("server-deb-root"))
    runtimeDir.set(layout.buildDirectory.dir("server-runtime"))
    outputFile.set(layout.buildDirectory.file("distributions/onewpipe-server_${serverDebVersion}_${serverDebArch}.deb"))
}
