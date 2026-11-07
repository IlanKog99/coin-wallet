package com.ilankog.coinwallet.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

fun interface HttpFetcher {
    @Throws(IOException::class)
    fun get(url: String): HttpResponse
}

/** Blocking GET; call from Dispatchers.IO. Only untested glue in the data layer. */
class UrlConnectionFetcher(private val timeoutMs: Int = 10_000) : HttpFetcher {
    override fun get(url: String): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
