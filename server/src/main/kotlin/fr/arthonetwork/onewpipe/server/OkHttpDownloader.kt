package fr.arthonetwork.onewpipe.server

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp-backed [Downloader] for the NewPipeExtractor used by the server.
 * Browser-like headers are important: YouTube serves different (or no) content
 * to non-browser clients.
 */
class OkHttpDownloader(client: OkHttpClient) : Downloader() {

    private val httpClient = client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val builder = OkRequest.Builder()
            .url(request.url())
            .apply {
                request.headers().forEach { (name, values) ->
                    values.forEach { header(name, it) }
                }
            }
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )

        when (request.httpMethod().uppercase()) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> builder.post(request.dataToSend()?.toRequestBody() ?: RequestBody.create(null, ByteArray(0)))
            else -> builder.get()
        }

        val response = try {
            httpClient.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw e
        }

        response.use { resp ->
            val headers = resp.headers.toMultimap().mapValues { it.value }
            val body = resp.body?.string() ?: ""
            return Response(
                resp.code,
                resp.message,
                headers,
                body,
                resp.request.url.toString()
            )
        }
    }
}
