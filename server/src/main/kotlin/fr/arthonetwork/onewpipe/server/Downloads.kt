package fr.arthonetwork.onewpipe.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.streams.Mp4FromDashWriter
import org.schabi.newpipe.streams.WebMReader
import org.schabi.newpipe.streams.WebMWriter
import org.schabi.newpipe.streams.io.SharpStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/*
 * Downloads for the web interface. YouTube serves every quality above 360p as
 * separate video and audio streams, which a browser cannot combine, so the
 * server downloads both and muxes them with NewPipe's own muxers (MP4 for
 * H.264 + AAC, WebM for VP9 + Opus). The page only starts a job, polls its
 * progress and saves the finished file: nothing blocks it, and a job can be
 * cancelled at any time.
 */

@Serializable
data class DownloadOptionDto(
    /** Opaque id to pass back when starting the download. */
    val id: String,
    val label: String,
    /** "video" or "audio". */
    val kind: String,
    val extension: String,
    /** Estimated size in bytes, or -1 when unknown. */
    val sizeBytes: Long = -1
)

@Serializable
data class DownloadOptionsDto(val title: String, val options: List<DownloadOptionDto>)

@Serializable
data class DownloadRequest(val url: String, val option: String)

@Serializable
data class DownloadJobDto(
    val id: String,
    val url: String,
    val title: String,
    val fileName: String,
    /** "queued", "downloading", "muxing", "done", "failed" or "cancelled". */
    val status: String,
    /** 0..1, or -1 while it cannot be measured (queued, muxing). */
    val progress: Double,
    val sizeBytes: Long = 0,
    val error: String? = null,
    val createdAt: Long
)

// ---- Options ------------------------------------------------------------------

private fun Stream.downloadable() = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !content.isNullOrBlank()

private fun Stream.estimatedSize(): Long = itagItem?.contentLength?.takeIf { it > 0 } ?: -1

private fun MediaFormat?.isWebmAudio() = this == MediaFormat.WEBMA || this == MediaFormat.WEBMA_OPUS

/** "1080p", "1080p60": the frame rate only when it is above the usual 30. */
private fun VideoStream.qualityLabel() = if (fps > 30) "${height}p$fps" else "${height}p"

private fun VideoStream.containerLabel() = when (format) {
    MediaFormat.MPEG_4 -> "MP4"
    MediaFormat.WEBM -> "WebM"
    else -> format?.name ?: "?"
}

/** The audio stream muxed with [video]: AAC for MP4, Opus/Vorbis WebM for WebM. */
private fun audioFor(video: VideoStream, audio: List<AudioStream>): AudioStream? = when (video.format) {
    MediaFormat.MPEG_4 -> audio.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
    MediaFormat.WEBM -> audio.filter { it.format.isWebmAudio() }.maxByOrNull { it.averageBitrate }
    else -> null
}

private fun sizeSum(vararg sizes: Long): Long = if (sizes.any { it < 0 }) -1 else sizes.sum()

internal fun downloadOptions(info: StreamInfo): List<DownloadOptionDto> {
    val audio = info.audioStreams.orEmpty().filter { it.downloadable() }
    val options = mutableListOf<DownloadOptionDto>()
    val seen = mutableSetOf<String>()

    // videoStreams holds the few streams with audio; videoOnlyStreams every other quality.
    val videos = info.videoOnlyStreams.orEmpty().filter { it.downloadable() }.map { it to true } +
        info.videoStreams.orEmpty().filter { it.downloadable() }.map { it to false }
    videos
        .sortedWith(compareByDescending<Pair<VideoStream, Boolean>> { it.first.height }.thenByDescending { it.first.fps })
        .forEach { (video, videoOnly) ->
            val key = "${video.qualityLabel()}|${video.format}"
            if (key in seen) return@forEach
            val extension = when (video.format) {
                MediaFormat.MPEG_4 -> "mp4"
                MediaFormat.WEBM -> "webm"
                else -> return@forEach
            }
            if (videoOnly) {
                val pair = audioFor(video, audio) ?: return@forEach
                seen += key
                options += DownloadOptionDto(
                    id = "v:${video.id}:${pair.id}",
                    label = "${video.qualityLabel()} · ${video.containerLabel()}",
                    kind = "video",
                    extension = extension,
                    sizeBytes = sizeSum(video.estimatedSize(), pair.estimatedSize())
                )
            } else {
                seen += key
                options += DownloadOptionDto(
                    id = "m:${video.id}",
                    label = "${video.qualityLabel()} · ${video.containerLabel()}",
                    kind = "video",
                    extension = extension,
                    sizeBytes = video.estimatedSize()
                )
            }
        }

    audio.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }?.let {
        options += DownloadOptionDto("a:${it.id}", "Audio · M4A${bitrateLabel(it)}", "audio", "m4a", it.estimatedSize())
    }
    audio.filter { it.format.isWebmAudio() }.maxByOrNull { it.averageBitrate }?.let {
        options += DownloadOptionDto("a:${it.id}", "Audio · WebM (Opus)${bitrateLabel(it)}", "audio", "webm", it.estimatedSize())
    }
    return options
}

private fun bitrateLabel(stream: AudioStream) =
    stream.averageBitrate.takeIf { it > 0 }?.let { " · $it kbps" }.orEmpty()

// ---- Jobs -----------------------------------------------------------------------

private enum class DownloadStatus(val wire: String, val finished: Boolean) {
    QUEUED("queued", false),
    DOWNLOADING("downloading", false),
    MUXING("muxing", false),
    DONE("done", true),
    FAILED("failed", true),
    CANCELLED("cancelled", true)
}

private class DownloadJob(
    val id: String,
    val owner: String,
    val url: String,
    val title: String,
    val fileName: String,
    val directory: File
) {
    val createdAt = System.currentTimeMillis()

    @Volatile var status = DownloadStatus.QUEUED
    @Volatile var progress = -1.0
    @Volatile var error: String? = null
    @Volatile var output: File? = null
    @Volatile var finishedAt = 0L
    @Volatile var worker: Job? = null
    @Volatile var currentCall: Call? = null

    fun toDto() = DownloadJobDto(
        id = id,
        url = url,
        title = title,
        fileName = fileName,
        status = status.wire,
        progress = if (status == DownloadStatus.DONE) 1.0 else progress,
        sizeBytes = output?.takeIf { status == DownloadStatus.DONE }?.length() ?: 0,
        error = error,
        createdAt = createdAt
    )
}

private val downloadJobs = ConcurrentHashMap<String, DownloadJob>()
private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
/** Two downloads at a time; the others wait in the queue. */
private val downloadSlots = Semaphore(2)
private val jobRandom = SecureRandom()
private const val FINISHED_JOB_TTL_MS = 6 * 60 * 60 * 1000L
private const val CHUNK_SIZE = 10L * 1024 * 1024

private val downloadRoot: File by lazy {
    File(System.getProperty("java.io.tmpdir"), "onewpipe-downloads").apply {
        // Files of a previous run cannot be claimed any more.
        deleteRecursively()
        mkdirs()
    }
}

private val downloadClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private const val DOWNLOAD_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

internal fun safeFileName(title: String, extension: String): String {
    val base = title
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
        .take(120)
        .ifBlank { "video" }
    return "$base.$extension"
}

private fun removeExpiredJobs() {
    val now = System.currentTimeMillis()
    downloadJobs.values
        .filter { it.status.finished && now - it.finishedAt > FINISHED_JOB_TTL_MS }
        .forEach { removeJob(it) }
}

private fun removeJob(job: DownloadJob) {
    downloadJobs.remove(job.id)
    job.directory.deleteRecursively()
}

private fun List<Stream>?.withId(id: String): Stream? = this.orEmpty().firstOrNull { it.id == id && it.downloadable() }

private fun startDownload(owner: String, url: String, optionId: String): DownloadJob {
    val info = cachedStreamInfo(url)
    val option = downloadOptions(info).firstOrNull { it.id == optionId }
        ?: throw IllegalArgumentException("This quality is not available any more")
    val parts = optionId.split(':')
    val sources: List<Stream> = when (parts.first()) {
        "v" -> listOf(info.videoOnlyStreams.withId(parts[1]), info.audioStreams.withId(parts[2]))
            .map { it ?: throw IllegalArgumentException("This quality is not available any more") }
        "m" -> listOfNotNull(info.videoStreams.withId(parts[1]))
        "a" -> listOfNotNull(info.audioStreams.withId(parts[1]))
        else -> emptyList()
    }
    if (sources.isEmpty()) throw IllegalArgumentException("This quality is not available any more")

    removeExpiredJobs()
    val id = ByteArray(16).also(jobRandom::nextBytes).joinToString("") { "%02x".format(it) }
    val directory = File(downloadRoot, id).apply { mkdirs() }
    val job = DownloadJob(id, owner, url, info.name.orEmpty(), safeFileName(info.name.orEmpty(), option.extension), directory)
    downloadJobs[id] = job
    job.worker = downloadScope.launch { runJob(job, sources, option) }
    return job
}

private suspend fun runJob(job: DownloadJob, sources: List<Stream>, option: DownloadOptionDto) {
    try {
        downloadSlots.withPermit {
            job.status = DownloadStatus.DOWNLOADING
            job.progress = 0.0
            val estimates = sources.map { it.estimatedSize() }
            val files = sources.mapIndexed { index, _ -> File(job.directory, "part$index") }
            var completed = 0L
            sources.forEachIndexed { index, stream ->
                val known = estimates.sum().takeIf { estimates.all { it > 0 } }
                downloadInChunks(job, stream.content, files[index]) { position, total ->
                    val overall = known ?: (completed + total.coerceAtLeast(position))
                    job.progress = ((completed + position).toDouble() / overall).coerceIn(0.0, 1.0) * 0.97
                }
                completed += files[index].length()
            }

            val output = File(job.directory, job.fileName)
            job.status = DownloadStatus.MUXING
            job.progress = -1.0
            withContext(Dispatchers.IO) { mux(sources, files, output, option) }
            files.forEach { it.delete() }
            job.output = output
            job.status = DownloadStatus.DONE
            job.progress = 1.0
        }
    } catch (e: CancellationException) {
        job.status = DownloadStatus.CANCELLED
        job.directory.deleteRecursively()
    } catch (e: Exception) {
        if (job.status != DownloadStatus.CANCELLED) {
            job.status = DownloadStatus.FAILED
            job.error = e.message ?: "Download failed"
        }
        job.directory.deleteRecursively()
    } finally {
        job.finishedAt = System.currentTimeMillis()
        job.currentCall = null
    }
}

/**
 * Downloads [url] with successive 10 MB range requests: YouTube throttles a
 * single plain GET to about playback speed, but serves ranges at full speed.
 */
private suspend fun downloadInChunks(job: DownloadJob, url: String, destination: File, onProgress: (Long, Long) -> Unit) {
    RandomAccessFile(destination, "rw").use { output ->
        output.setLength(0)
        var position = 0L
        var total = -1L
        while (total < 0 || position < total) {
            coroutineContext.ensureActive()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .header("Range", "bytes=$position-${position + CHUNK_SIZE - 1}")
                .build()
            val call = downloadClient.newCall(request)
            job.currentCall = call
            call.execute().use { response ->
                if (response.code != 206 && response.code != 200) throw IOException("The video host answered HTTP ${response.code}")
                if (response.code == 206) {
                    total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: throw IOException("The video host sent no size")
                } else {
                    // The host ignored the range and sends the whole file.
                    position = 0
                    output.setLength(0)
                    total = response.body.contentLength()
                }
                val input = response.body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                output.seek(position)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    received += read
                    onProgress(position + received, total)
                }
                if (received == 0L) throw IOException("The video host sent an empty response")
                position += received
                if (total < 0) total = position
            }
        }
    }
}

private fun mux(sources: List<Stream>, files: List<File>, output: File, option: DownloadOptionDto) {
    output.delete()
    if (sources.size == 1) {
        // A stream that already holds everything it needs. M4A audio is a
        // DASH file; rewrite it as a plain M4A that every player accepts.
        if (option.extension == "m4a") {
            val rewritten = runCatching {
                FileSharpStream(files[0]).use { source ->
                    FileSharpStream(output).use { out ->
                        Mp4FromDashWriter(source).apply {
                            setMainBrand(0x4D344120) // "M4A "
                            parseSources()
                            selectTracks(0)
                            build(out)
                        }
                    }
                }
            }.isSuccess
            if (rewritten) return
            output.delete()
        }
        Files.move(files[0].toPath(), output.toPath())
        return
    }

    FileSharpStream(files[0]).use { video ->
        FileSharpStream(files[1]).use { audio ->
            FileSharpStream(output).use { out ->
                if (option.extension == "mp4") {
                    Mp4FromDashWriter(video, audio).apply {
                        parseSources()
                        selectTracks(0, 0)
                        build(out)
                    }
                } else {
                    WebMWriter(video, audio).apply {
                        parseSources()
                        // YouTube WebM audio can carry a fake cover-art video track: pick its audio track.
                        val audioTrack = getTracksFromSource(1).indexOfFirst { it.kind == WebMReader.TrackKind.Audio }
                        selectTracks(0, audioTrack.coerceAtLeast(0))
                        build(out)
                    }
                }
            }
        }
    }
    if (output.length() == 0L) throw IOException("The video and audio could not be combined")
}

/** [SharpStream] over a file, as NewPipe's muxers expect. */
private class FileSharpStream(file: File) : SharpStream() {
    private var source: RandomAccessFile? = RandomAccessFile(file, "rw")
    private val file get() = source ?: throw IOException("Stream closed")

    override fun read(): Int = file.read()
    override fun read(buffer: ByteArray): Int = file.read(buffer)
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int = file.read(buffer, offset, count)
    override fun skip(amount: Long): Long {
        val target = (file.filePointer + amount).coerceAtMost(file.length())
        val skipped = target - file.filePointer
        file.seek(target)
        return skipped
    }
    override fun available(): Long = runCatching { file.length() - file.filePointer }.getOrDefault(0)
    override fun rewind() = file.seek(0)
    override fun isClosed() = source == null
    override fun close() {
        runCatching { source?.close() }
        source = null
    }
    override fun canRewind() = true
    override fun canRead() = true
    override fun canWrite() = true
    override fun canSetLength() = true
    override fun canSeek() = true
    override fun write(value: Byte) = file.write(value.toInt())
    override fun write(buffer: ByteArray) = file.write(buffer)
    override fun write(buffer: ByteArray, offset: Int, count: Int) = file.write(buffer, offset, count)
    override fun setLength(length: Long) = file.setLength(length)
    override fun seek(offset: Long) = file.seek(offset)
    override fun length(): Long = file.length()
}

private fun cancelJob(job: DownloadJob) {
    if (job.status.finished) return
    job.status = DownloadStatus.CANCELLED
    job.currentCall?.cancel()
    job.worker?.cancel()
}

// ---- Routes -------------------------------------------------------------------

/**
 * Downloads use the server's disk and bandwidth, so only the local machine
 * (the desktop app) or a signed-in account may start them. Job ids are long
 * random tokens: whoever started a job can follow and save it.
 */
private suspend fun ApplicationCall.downloadOwner(jwtSecret: String, store: Store): String? {
    val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim().orEmpty()
    if (token.isNotEmpty()) {
        val claims = Jwt.verifyClaims(jwtSecret, token)
        val username = claims?.sub
        if (claims != null && username != null && store.findAccount(username) != null &&
            store.isSessionValid(username, claims.iat)
        ) {
            return "user:$username"
        }
    }
    val address = request.local.remoteAddress
    if (address == "127.0.0.1" || address == "::1" || address == "0:0:0:0:0:0:0:1") return "local"
    respond(HttpStatusCode.Unauthorized, ErrorResponse("Sign in to download from this server"))
    return null
}

private fun contentDisposition(fileName: String): String {
    val ascii = fileName.replace(Regex("[^\\x20-\\x7E]"), "_").replace("\"", "'")
    val encoded = URLEncoder.encode(fileName, Charsets.UTF_8).replace("+", "%20")
    return "attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded"
}

fun Route.downloadRoutes(jwtSecret: String, store: Store) {
    get("/api/downloads/options") {
        val url = call.request.queryParameters["url"].orEmpty()
        if (url.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing url"))
            return@get
        }
        val info = withContext(Dispatchers.IO) { cachedStreamInfo(url) }
        call.respond(DownloadOptionsDto(info.name.orEmpty(), downloadOptions(info)))
    }

    get("/api/downloads") {
        val owner = call.downloadOwner(jwtSecret, store) ?: return@get
        removeExpiredJobs()
        call.respond(downloadJobs.values.filter { it.owner == owner }.sortedByDescending { it.createdAt }.map { it.toDto() })
    }

    post("/api/downloads") {
        val owner = call.downloadOwner(jwtSecret, store) ?: return@post
        val request = runCatching { call.receive<DownloadRequest>() }.getOrNull()
        if (request == null || request.url.isBlank() || request.option.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Expected {\"url\": …, \"option\": …}"))
            return@post
        }
        val job = try {
            withContext(Dispatchers.IO) { startDownload(owner, request.url, request.option) }
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid download"))
            return@post
        }
        call.respond(HttpStatusCode.Created, job.toDto())
    }

    get("/api/downloads/{id}") {
        val job = downloadJobs[call.parameters["id"].orEmpty()]
        if (job == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown download"))
        else call.respond(job.toDto())
    }

    delete("/api/downloads/{id}") {
        val job = downloadJobs[call.parameters["id"].orEmpty()]
        if (job == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown download"))
            return@delete
        }
        if (job.status.finished) removeJob(job) else cancelJob(job)
        call.respond(HttpStatusCode.NoContent)
    }

    get("/api/downloads/{id}/file") {
        val job = downloadJobs[call.parameters["id"].orEmpty()]
        val file = job?.output?.takeIf { job.status == DownloadStatus.DONE && it.exists() }
        if (job == null || file == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("This download is not ready or has expired"))
            return@get
        }
        call.response.header(HttpHeaders.ContentDisposition, contentDisposition(job.fileName))
        call.respondFile(file)
    }
}
