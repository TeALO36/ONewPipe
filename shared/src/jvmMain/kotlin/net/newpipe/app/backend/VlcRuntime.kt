package net.newpipe.app.backend

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Makes the VLC runtime shipped with the desktop distribution discoverable.
 *
 * vlcj is only the Java binding; without this explicit lookup a clean Windows
 * machine must already have VLC installed, which made the MSI player fail as
 * soon as a video was opened.
 */
object VlcRuntime {
    private var prepared = false

    @Synchronized
    fun prepare(): Boolean {
        if (prepared) return true

        val runtimeDirectory = locateRuntime() ?: return false
        val directory = runtimeDirectory.toAbsolutePath().toString()
        val plugins = runtimeDirectory.resolve("plugins").toAbsolutePath().toString()

        val discovered = runCatching {
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), directory)
            // NativeDiscovery also sets VLC_PLUGIN_PATH through libvlc's platform
            // binding, which is required for codecs and demuxers to be loaded.
            val bundledStrategy = object : NativeDiscoveryStrategy {
                override fun supported() = true
                override fun discover() = directory
                override fun onFound(path: String): Boolean {
                    NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), path)
                    return true
                }
                override fun onSetPluginPath(path: String): Boolean =
                    LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$plugins") == 0
            }
            NativeDiscovery(bundledStrategy).discover()
        }.getOrDefault(false)

        // If a packaged runtime is unavailable or cannot be loaded, vlcj can
        // still discover a system VLC installation during player creation.
        prepared = discovered
        return discovered
    }

    private fun locateRuntime(): Path? {
        val candidates = buildList {
            System.getProperty("compose.application.resources.dir")
                ?.takeIf(String::isNotBlank)
                ?.let { add(Paths.get(it, "vlc")) }

            runCatching {
                val codeSource = VlcRuntime::class.java.protectionDomain.codeSource.location.toURI()
                add(Paths.get(codeSource).parent.resolve("resources/vlc"))
            }

            add(Paths.get(System.getProperty("user.dir"), "resources/vlc"))
            add(Paths.get(System.getProperty("user.dir"), "app/resources/vlc"))
        }

        return candidates.firstOrNull { path ->
            Files.isRegularFile(path.resolve("libvlc.dll")) ||
                Files.isRegularFile(path.resolve("libvlc.so")) ||
                Files.isRegularFile(path.resolve("libvlc.dylib"))
        }
    }
}
