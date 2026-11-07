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
private const val MAX_BODY_BYTES = 1_000_000 // real response is ~1 KB

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
            val body = stream?.use { readCapped(it) } ?: ""
            return HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }
}

// InputStream.readNBytes needs API 33; minSdk is 30.
private fun readCapped(input: java.io.InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        if (out.size() > MAX_BODY_BYTES) throw IOException("response larger than $MAX_BODY_BYTES bytes")
    }
    return out.toString(Charsets.UTF_8.name())
}
