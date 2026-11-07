package com.ilankog.coinwallet.core

import com.ilankog.coinwallet.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RatesTest {
    private val snapshot = RateSnapshot(
        fetchedAt = 0,
        date = "2026-11-06",
        rates = mapOf("EUR" to 1.0, "CZK" to 24.35, "ILS" to 3.5717, "JPY" to 176.77),
    )

    @Test
    fun `parses real Frankfurter response`() {
        val r = RatesParser.parse(Fixtures.text("frankfurter_latest.json")) as ParseResult.Ok
        assertEquals("2026-11-06", r.date)
        assertEquals(30, r.rates.size)
        assertEquals(24.35, r.rates.getValue("CZK"), 0.0)
        assertEquals(1.0, r.rates.getValue("EUR"), 0.0)
    }

    @Test
    fun `rejects malformed or foreign-base bodies`() {
        assertTrue(RatesParser.parse("<html>busy</html>") is ParseResult.Error)
        assertTrue(RatesParser.parse("""{"base":"USD","date":"2026-11-06","rates":{"EUR":0.9}}""") is ParseResult.Error)
        assertTrue(RatesParser.parse("""{"base":"EUR","date":"2026-11-06","rates":{}}""") is ParseResult.Error)
        assertTrue(RatesParser.parse("""{"base":"EUR","date":"2026-11-06"}""") is ParseResult.Error)
    }

    @Test
    fun `drops unknown codes and non-positive rates`() {
        val body = """{"base":"EUR","date":"2026-11-06","rates":{"CZK":24.35,"XAU":0.0004,"USD":0,"GBP":-1.0}}"""
        val r = RatesParser.parse(body) as ParseResult.Ok
        assertEquals(setOf("EUR", "CZK"), r.rates.keys)
    }

    @Test
    fun `cross rate divides EUR based rates`() {
        assertEquals(BigDecimal("0.1466817248"), Conversion.crossRate(snapshot, "CZK", "ILS"))
        assertEquals(BigDecimal.ONE, Conversion.crossRate(null, "CZK", "CZK"))
        assertNull(Conversion.crossRate(snapshot, "THB", "ILS"))
        assertNull(Conversion.crossRate(null, "CZK", "ILS"))
    }

    @Test
    fun `convert rounds half even into target minor units`() {
        val czk = Currencies.orFallback("CZK")
        val ils = Currencies.orFallback("ILS")
        val jpy = Currencies.orFallback("JPY")
        val eur = Currencies.orFallback("EUR")
        assertEquals(1467L, Conversion.convert(10000, czk, ils, BigDecimal("0.1466817248")))
        assertEquals(566L, Conversion.convert(1000, jpy, eur, BigDecimal("0.0056570685")))
        assertEquals(1768L, Conversion.convert(1000, eur, jpy, BigDecimal("176.77")))
        assertEquals(2L, Conversion.convert(25, eur, eur, BigDecimal("0.1"))) // 0.025 -> 0.02 (half even)
    }

    @Test
    fun `resolve prefers same then manual then ECB`() {
        assertEquals(RateSource.SAME, Conversion.resolve("ILS", "ILS", null, null)?.source)
        val manual = ManualRate(BigDecimal("0.15"), "ILS")
        assertEquals(ResolvedRate(BigDecimal("0.15"), RateSource.MANUAL), Conversion.resolve("CZK", "ILS", manual, snapshot))
        assertEquals(RateSource.ECB, Conversion.resolve("CZK", "ILS", null, snapshot)?.source)
        assertNull(Conversion.resolve("CZK", "ILS", null, null))
    }

    @Test
    fun `manual rate saved for another home is ignored`() {
        val manual = ManualRate(BigDecimal("0.04"), "EUR")
        assertEquals(RateSource.ECB, Conversion.resolve("CZK", "ILS", manual, snapshot)?.source)
        assertNull(Conversion.resolve("CZK", "ILS", manual, null))
    }

    @Test
    fun `stale when missing, older than 12h, or from the future`() {
        val now = 100_000_000L
        assertTrue(Staleness.isStale(null, now))
        assertFalse(Staleness.isStale(snapshot.copy(fetchedAt = now - Staleness.MAX_AGE_MS), now))
        assertTrue(Staleness.isStale(snapshot.copy(fetchedAt = now - Staleness.MAX_AGE_MS - 1), now))
        assertTrue(Staleness.isStale(snapshot.copy(fetchedAt = now + 1), now))
    }

    @Test
    fun `drops implausible rates so conversions cannot overflow`() {
        val body = """{"base":"EUR","date":"2026-11-06","rates":{"CZK":24.35,"JPY":1e-15,"USD":1e12,"ILS":0}}"""
        val r = RatesParser.parse(body) as ParseResult.Ok
        assertEquals(setOf("EUR", "CZK"), r.rates.keys)
    }

    @Test
    fun `convert returns null instead of throwing on overflow`() {
        val idr = Currencies.orFallback("IDR")
        val eur = Currencies.orFallback("EUR")
        assertNull(Conversion.convert(Long.MAX_VALUE / 2, eur, idr, BigDecimal("1e15")))
    }
}
