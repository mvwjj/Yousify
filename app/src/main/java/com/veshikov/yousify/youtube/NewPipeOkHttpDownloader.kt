package com.veshikov.yousify.youtube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

class NewPipeOkHttpDownloader : Downloader() {
    private val client = OkHttpClient()

    override fun execute(request: Request): Response {
        // 1) Build the OkHttp request, copying over all headers
        val builder = OkHttpRequest.Builder()
            .url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { value ->
                builder.addHeader(name, value)
            }
        }

        // 2) If there's a body, wrap it in a RequestBody
        val data = request.dataToSend()
        val contentTypeHeader = request.headers()["Content-Type"]?.firstOrNull()
        val mediaType = contentTypeHeader?.toMediaTypeOrNull()
        val body: RequestBody? = data?.let {
            if (mediaType != null) {
                RequestBody.create(mediaType, it)
            } else {
                RequestBody.create(null, it)
            }
        }
        builder.method(request.httpMethod(), body)

        // 3) Execute it and translate to NewPipe's Response
        val okRes = client.newCall(builder.build()).execute()
        okRes.use { response ->
            val bodyString = response.body?.string().orEmpty()
            return Response(
                response.code,
                response.message,                            // the HTTP status message
                response.headers.toMultimap(),               // all headers
                bodyString,                                  // the response body
                response.request.url.toString()              // the final URL
            )
        }
    }
}
