package app.sobre.player.data.extractor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class SobreDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val body = request.dataToSend()
        val builder = Request.Builder()
            .url(request.url())
            .method(
                request.httpMethod(),
                body?.toRequestBody(null)
            )

        for ((key, values) in request.headers()) {
            for (value in values) {
                builder.addHeader(key, value)
            }
        }

        val response = client.newCall(builder.build()).execute()

        val responseHeaders = mutableMapOf<String, List<String>>()
        for (name in response.headers.names()) {
            responseHeaders[name] = response.headers.values(name)
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            response.body?.string(),
            response.request.url.toString()
        )
    }

    companion object {
        val instance: SobreDownloader by lazy { SobreDownloader() }
    }
}
