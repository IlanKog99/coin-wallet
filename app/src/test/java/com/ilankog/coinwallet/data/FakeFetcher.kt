package com.ilankog.coinwallet.data

class FakeFetcher(var response: () -> HttpResponse) : HttpFetcher {
    @Volatile var calls = 0
    @Volatile var lastUrl: String? = null

    override fun get(url: String): HttpResponse {
        calls++
        lastUrl = url
        return response()
    }
}
