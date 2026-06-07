package net.newpipe.app.backend

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder()
            .method(method, dataToSend?.toRequestBody())
            .url(url)

        headers?.forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        val okHttpRequest = builder.build()
        try {
            val response = client.newCall(okHttpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMultimap()

            return Response(
                response.code,
                response.message,
                responseHeaders,
                responseBody,
                request.url()
            )
        } catch (e: IOException) {
            throw org.schabi.newpipe.extractor.exceptions.ExtractionException("Network error", e)
        }
    }
}
