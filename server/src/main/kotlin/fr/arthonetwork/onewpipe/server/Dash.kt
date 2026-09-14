package fr.arthonetwork.onewpipe.server

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/*
 * YouTube no longer serves streams that contain both video and audio, so a
 * plain <video src> can only play the audio. Browsers play YouTube the way
 * youtube.com does: a DASH manifest listing video-only and audio-only
 * streams, fetched by range requests.
 *
 * The manifest points every stream at /api/stream on this server, which relays
 * the ranges to googlevideo. That keeps the browser clear of CORS and of the
 * URLs being bound to the IP that extracted them. Only URLs this server
 * extracted can be relayed (through random tokens), so it is not an open proxy.
 */

private data class RelayTarget(val url: String, val expiresAt: Long)

private val relayTargets = ConcurrentHashMap<String, RelayTarget>()
private const val RELAY_TTL_MS = 6 * 60 * 60 * 1000L
private val random = SecureRandom()

private val relayClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private const val RELAY_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

/** Registers a stream URL for relaying and returns its token. */
internal fun registerRelayTarget(url: String): String {
    val now = System.currentTimeMillis()
    if (relayTargets.size > 5_000) relayTargets.entries.removeIf { it.value.expiresAt < now }
    val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    relayTargets[token] = RelayTarget(url, now + RELAY_TTL_MS)
    return token
}

internal fun relayTarget(token: String): String? =
    relayTargets[token]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.url

/**
 * Builds one DASH manifest with every video-only and audio-only stream of
 * [videoUrl]. [relayUrl] turns a relay token into the URL written as BaseURL.
 */
suspend fun buildDashManifest(videoUrl: String, relayUrl: (String) -> String): String =
    withContext(Dispatchers.IO) {
        val info = cachedStreamInfo(videoUrl)
        val duration = info.duration ?: 0L

        val streams: List<Stream> = info.videoOnlyStreams.orEmpty() + info.audioStreams.orEmpty()
        val manifests = streams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .mapNotNull { stream ->
                val itagItem = stream.itagItem ?: return@mapNotNull null
                runCatching {
                    YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                        relayUrl(registerRelayTarget(stream.content)),
                        itagItem,
                        duration
                    )
                }.getOrNull()
            }
        if (manifests.isEmpty()) throw IllegalArgumentException("No DASH-compatible stream found")
        mergeManifests(manifests)
    }

/**
 * Each generated manifest describes a single stream. They are merged into one
 * Period with one AdaptationSet per MIME type, so players can switch
 * qualities within the video set and pair it with an audio set.
 */
internal fun mergeManifests(manifests: List<String>): String {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val builder = factory.newDocumentBuilder()
    val base = builder.parse(InputSource(StringReader(manifests.first())))
    val period = base.getElementsByTagNameNS("*", "Period").item(0) as Element

    // Keep the first AdaptationSet of each MIME type; move the others' representations into it.
    val setsByMime = linkedMapOf<String, Element>()
    val allSets = mutableListOf<Element>()
    val baseSets = base.getElementsByTagNameNS("*", "AdaptationSet")
    for (i in 0 until baseSets.length) allSets += baseSets.item(i) as Element
    for (xml in manifests.drop(1)) {
        val doc = builder.parse(InputSource(StringReader(xml)))
        val sets = doc.getElementsByTagNameNS("*", "AdaptationSet")
        for (i in 0 until sets.length) allSets += base.importNode(sets.item(i), true) as Element
    }
    for (set in allSets) {
        val mime = set.getAttribute("mimeType")
        val target = setsByMime[mime]
        if (target == null) {
            setsByMime[mime] = set
            if (set.parentNode == null) period.appendChild(set)
        } else {
            val representations = set.getElementsByTagNameNS("*", "Representation")
            (0 until representations.length).map { representations.item(it) }.forEach { target.appendChild(it) }
            set.parentNode?.removeChild(set)
        }
    }

    val sets = base.getElementsByTagNameNS("*", "AdaptationSet")
    for (i in 0 until sets.length) (sets.item(i) as Element).setAttribute("id", i.toString())
    val representations = base.getElementsByTagNameNS("*", "Representation")
    for (i in 0 until representations.length) (representations.item(i) as Element).setAttribute("id", "r$i")

    val writer = StringWriter()
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        setOutputProperty(OutputKeys.INDENT, "no")
    }.transform(DOMSource(base), StreamResult(writer))
    return writer.toString()
}

fun Route.dashRoutes() {
    get("/api/manifest") {
        val url = call.request.queryParameters["url"].orEmpty()
        if (url.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing url"))
            return@get
        }
        val manifest = buildDashManifest(url) { token -> "/api/stream?t=$token" }
        call.respondText(manifest, ContentType.parse("application/dash+xml"))
    }

    get("/api/stream") {
        val target = relayTarget(call.request.queryParameters["t"].orEmpty())
        if (target == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown or expired stream"))
            return@get
        }
        val request = Request.Builder()
            .url(target)
            .header("User-Agent", RELAY_USER_AGENT)
            .apply { call.request.headers[HttpHeaders.Range]?.let { header(HttpHeaders.Range, it) } }
            .build()
        val response = withContext(Dispatchers.IO) { relayClient.newCall(request).execute() }
        val body = response.body
        call.respond(object : OutgoingContent.WriteChannelContent() {
            override val status = HttpStatusCode.fromValue(response.code)
            override val contentType = response.header("Content-Type")?.let { ContentType.parse(it) }
            override val contentLength = body.contentLength().takeIf { it >= 0 }
            override val headers = Headers.build {
                append(HttpHeaders.AcceptRanges, "bytes")
                response.header("Content-Range")?.let { append(HttpHeaders.ContentRange, it) }
            }

            override suspend fun writeTo(channel: ByteWriteChannel) {
                response.use {
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = withContext(Dispatchers.IO) { input.read(buffer) }
                        if (read < 0) break
                        channel.writeFully(buffer, 0, read)
                    }
                }
            }
        })
    }
}
