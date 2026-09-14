package fr.arthonetwork.onewpipe.server

import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Builds the DASH manifest of a real YouTube video, as the web player requests
 * it. Skips (passes) when the network is unavailable.
 */
class DashManifestTest {

    @Test
    fun manifestListsVideoAndAudioThroughTheRelay() = runBlocking {
        NewPipe.init(OkHttpDownloader(okhttp3.OkHttpClient.Builder().build()))
        val candidates = runCatching { searchMedia(0, "official music video") }.getOrElse {
            println("SKIP DashManifestTest (search failed): ${it.message}")
            return@runBlocking
        }
        val url = candidates.firstOrNull { it.durationText.isNotBlank() }?.url ?: run {
            println("SKIP DashManifestTest: no video in the search results")
            return@runBlocking
        }

        val manifest = buildDashManifest(url) { token -> "/api/stream?t=$token" }
        println("MANIFEST (${manifest.length} chars): ${manifest.take(900)}")

        val sets = Regex("<AdaptationSet[^>]*mimeType=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        val tokens = Regex("/api/stream\\?t=([0-9a-f]{32})").findAll(manifest).map { it.groupValues[1] }.toList()
        println("ADAPTATION SETS: $sets, representations: ${Regex("<Representation ").findAll(manifest).count()}, tokens: ${tokens.size}")

        assertTrue(sets.any { it.startsWith("video/") }, "Expected a video AdaptationSet, got $sets")
        assertTrue(sets.any { it.startsWith("audio/") }, "Expected an audio AdaptationSet, got $sets")
        assertTrue(sets.size == sets.distinct().size, "Expected one AdaptationSet per MIME type, got $sets")
        assertTrue(tokens.isNotEmpty() && tokens.all { relayTarget(it) != null }, "Every stream should use a registered relay token")
        assertTrue(relayTarget("0".repeat(32)) == null, "Unknown tokens must not resolve")
    }
}
