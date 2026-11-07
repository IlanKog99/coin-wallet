package com.ilankog.coinwallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

/** EUR-based ECB rates; always contains "EUR" to 1.0. */
@Serializable
data class RateSnapshot(val fetchedAt: Long, val date: String, val rates: Map<String, Double>)

/** "1 trip currency = [rate] [home]", valid only while the home currency equals [home]. */
data class ManualRate(val rate: BigDecimal, val home: String)

enum class RateSource { SAME, MANUAL, ECB }

data class ResolvedRate(val rate: BigDecimal, val source: RateSource)

sealed interface ParseResult {
    data class Ok(val date: String, val rates: Map<String, Double>) : ParseResult
    data class Error(val reason: String) : ParseResult
}

object RatesParser {
    @Serializable
    private data class Body(val base: String, val date: String, val rates: Map<String, Double>)

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): ParseResult {
        val parsed = try {
            json.decodeFromString(Body.serializer(), body)
        } catch (e: IllegalArgumentException) { // SerializationException is an IllegalArgumentException
            return ParseResult.Error("malformed body: ${e.message}")
        }
        if (parsed.base != "EUR") return ParseResult.Error("unexpected base ${parsed.base}")
        val rates = parsed.rates.filter { (code, r) -> Currencies.get(code) != null && r.isFinite() && r > 0.0 } +
            ("EUR" to 1.0)
        if (rates.size < 2) return ParseResult.Error("no usable rates")
        return ParseResult.Ok(parsed.date, rates)
    }
}

object Conversion {
    private const val SCALE = 10

    fun crossRate(snapshot: RateSnapshot?, from: String, to: String): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val rates = snapshot?.rates ?: return null
        val f = rates[from] ?: return null
        val t = rates[to] ?: return null
        return BigDecimal.valueOf(t).divide(BigDecimal.valueOf(f), SCALE, RoundingMode.HALF_EVEN)
    }

    fun resolve(from: String, home: String, manual: ManualRate?, snapshot: RateSnapshot?): ResolvedRate? {
        if (from == home) return ResolvedRate(BigDecimal.ONE, RateSource.SAME)
        if (manual != null && manual.home == home) return ResolvedRate(manual.rate, RateSource.MANUAL)
        return crossRate(snapshot, from, home)?.let { ResolvedRate(it, RateSource.ECB) }
    }

    fun convert(minor: Long, from: CurrencyInfo, to: CurrencyInfo, rate: BigDecimal): Long =
        BigDecimal.valueOf(minor, from.decimals)
            .multiply(rate)
            .movePointRight(to.decimals)
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact()
}

object Staleness {
    const val MAX_AGE_MS: Long = 12L * 60 * 60 * 1000

    fun isStale(snapshot: RateSnapshot?, now: Long): Boolean =
        snapshot == null || snapshot.fetchedAt > now || now - snapshot.fetchedAt > MAX_AGE_MS
}
