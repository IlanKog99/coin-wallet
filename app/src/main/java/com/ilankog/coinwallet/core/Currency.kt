package com.ilankog.coinwallet.core

import java.util.Locale

enum class DenomKind(val prefix: Char) { COIN('c'), NOTE('n') }

data class Denomination(val kind: DenomKind, val value: Long) {
    val key: String get() = "${kind.prefix}$value"
}

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val symbolFirst: Boolean,
    val decimals: Int,
    val coins: List<Long>,
    val notes: List<Long>,
) {
    val minorPerMajor: Long = (1..decimals).fold(1L) { acc, _ -> acc * 10 }
    val denominations: List<Denomination> =
        coins.map { Denomination(DenomKind.COIN, it) } + notes.map { Denomination(DenomKind.NOTE, it) }
}

object Currencies {
    const val FALLBACK = "EUR"

    private fun c(
        code: String, name: String, symbol: String, symbolFirst: Boolean, decimals: Int,
        coins: String, notes: String,
    ) = CurrencyInfo(
        code, name, symbol, symbolFirst, decimals,
        coins.split(' ').map(String::toLong), notes.split(' ').map(String::toLong),
    )

    // Values in minor units. Source of currency list: api.frankfurter.dev/v1/currencies (2026-11-07).
    val all: List<CurrencyInfo> = listOf(
        c("AUD", "Australian Dollar", "A$", true, 2, "5 10 20 50 100 200", "500 1000 2000 5000 10000"),
        c("BRL", "Brazilian Real", "R$", true, 2, "5 10 25 50 100", "200 500 1000 2000 5000 10000 20000"),
        c("CAD", "Canadian Dollar", "C$", true, 2, "5 10 25 100 200", "500 1000 2000 5000 10000"),
        c("CHF", "Swiss Franc", "CHF ", true, 2, "5 10 20 50 100 200 500", "1000 2000 5000 10000 20000 100000"),
        c("CNY", "Chinese Renminbi Yuan", "CN¥", true, 2, "10 50 100", "100 500 1000 2000 5000 10000"),
        c("CZK", "Czech Koruna", "Kč", false, 2, "100 200 500 1000 2000 5000", "10000 20000 50000 100000 200000 500000"),
        c("DKK", "Danish Krone", "kr", false, 2, "50 100 200 500 1000 2000", "5000 10000 20000 50000 100000"),
        c("EUR", "Euro", "€", true, 2, "1 2 5 10 20 50 100 200", "500 1000 2000 5000 10000 20000"),
        c("GBP", "British Pound", "£", true, 2, "1 2 5 10 20 50 100 200", "500 1000 2000 5000"),
        c("HKD", "Hong Kong Dollar", "HK$", true, 2, "10 20 50 100 200 500 1000", "1000 2000 5000 10000 50000 100000"),
        c("HUF", "Hungarian Forint", "Ft", false, 0, "5 10 20 50 100 200", "500 1000 2000 5000 10000 20000"),
        c("IDR", "Indonesian Rupiah", "Rp", true, 0, "100 200 500 1000", "1000 2000 5000 10000 20000 50000 100000"),
        c("ILS", "Israeli New Shekel", "₪", true, 2, "10 50 100 200 500 1000", "2000 5000 10000 20000"),
        c("INR", "Indian Rupee", "₹", true, 2, "100 200 500 1000 2000", "1000 2000 5000 10000 20000 50000"),
        c("ISK", "Icelandic Króna", "kr", false, 0, "1 5 10 50 100", "500 1000 2000 5000 10000"),
        c("JPY", "Japanese Yen", "¥", true, 0, "1 5 10 50 100 500", "1000 2000 5000 10000"),
        c("KRW", "South Korean Won", "₩", true, 0, "10 50 100 500", "1000 5000 10000 50000"),
        c("MXN", "Mexican Peso", "MX$", true, 2, "50 100 200 500 1000 2000", "2000 5000 10000 20000 50000 100000"),
        c("MYR", "Malaysian Ringgit", "RM", true, 2, "5 10 20 50", "100 500 1000 2000 5000 10000"),
        c("NOK", "Norwegian Krone", "kr", false, 2, "100 500 1000 2000", "5000 10000 20000 50000 100000"),
        c("NZD", "New Zealand Dollar", "NZ$", true, 2, "10 20 50 100 200", "500 1000 2000 5000 10000"),
        c("PHP", "Philippine Peso", "₱", true, 2, "25 100 500 1000 2000", "2000 5000 10000 20000 50000 100000"),
        c("PLN", "Polish Złoty", "zł", false, 2, "1 2 5 10 20 50 100 200 500", "1000 2000 5000 10000 20000 50000"),
        c("RON", "Romanian Leu", "lei", false, 2, "1 5 10 50", "100 500 1000 5000 10000 20000 50000"),
        c("SEK", "Swedish Krona", "kr", false, 2, "100 200 500 1000", "2000 5000 10000 20000 50000 100000"),
        c("SGD", "Singapore Dollar", "S$", true, 2, "5 10 20 50 100", "200 500 1000 5000 10000 100000"),
        c("THB", "Thai Baht", "฿", true, 2, "25 50 100 200 500 1000", "2000 5000 10000 50000 100000"),
        c("TRY", "Turkish Lira", "₺", true, 2, "1 5 10 25 50 100", "500 1000 2000 5000 10000 20000"),
        c("USD", "United States Dollar", "$", true, 2, "1 5 10 25 50 100", "100 200 500 1000 2000 5000 10000"),
        c("ZAR", "South African Rand", "R", true, 2, "10 20 50 100 200 500", "1000 2000 5000 10000 20000"),
    )

    private val byCode: Map<String, CurrencyInfo> = all.associateBy { it.code }

    fun get(code: String): CurrencyInfo? = byCode[code]

    fun orFallback(code: String): CurrencyInfo = byCode[code] ?: byCode.getValue(FALLBACK)

    /** Currency of the locale's country if supported, else EUR (documented fallback, not an error). */
    fun defaultHome(locale: Locale): String {
        if (locale.country.isEmpty()) return FALLBACK
        val code = try {
            java.util.Currency.getInstance(locale)?.currencyCode
        } catch (e: IllegalArgumentException) {
            null // unsupported country code: fall back
        }
        return code?.takeIf { byCode.containsKey(it) } ?: FALLBACK
    }
}

object CurrencySearch {
    fun filter(query: String): List<CurrencyInfo> {
        val q = query.trim()
        if (q.isEmpty()) return Currencies.all
        return Currencies.all.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
    }
}

object DenomTier {
    /** 0 = copper/low, 1 = brass/mid, 2 = silver/high, by position within its coin or note list. */
    fun of(d: Denomination, info: CurrencyInfo): Int {
        val list = if (d.kind == DenomKind.COIN) info.coins else info.notes
        val i = list.indexOf(d.value).coerceAtLeast(0)
        return (i * 3 / list.size).coerceIn(0, 2)
    }

    /** Short text printed on the coin/note art. */
    fun faceText(d: Denomination, info: CurrencyInfo): String {
        val v = d.value
        if (v % info.minorPerMajor != 0L) return v.toString()
        val major = v / info.minorPerMajor
        return if (major >= 10_000 && major % 1000 == 0L) "${major / 1000}K" else major.toString()
    }
}
