package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class TotalsTest {
    private val ils = Currencies.orFallback("ILS")
    private val snapshot = RateSnapshot(0, "2026-11-06", mapOf("EUR" to 1.0, "CZK" to 24.35, "ILS" to 3.5717, "JPY" to 176.77))
    private val prague = Trip("p", "Prague", "CZK", mapOf("n10000" to 1), createdAt = 1)    // 100.00 Kč
    private val tokyo = Trip("t", "Tokyo", "JPY", mapOf("n1000" to 1), createdAt = 2)       // ¥1000
    private val bangkok = Trip("b", "Bangkok", "THB", mapOf("n2000" to 1), createdAt = 0)   // no THB rate

    @Test
    fun `sums trips with rates and counts the rest`() {
        val totals = Totals.all(listOf(prague, tokyo, bangkok), ils, emptyMap(), snapshot)
        assertEquals(listOf("t", "p", "b"), totals.trips.map { it.trip.id })
        assertEquals(2021L, totals.trips[0].homeMinor)
        assertEquals(1467L, totals.trips[1].homeMinor)
        assertNull(totals.trips[2].homeMinor)
        assertEquals(3488L, totals.grandHomeMinor)
        assertEquals(1, totals.missingRateCount)
    }

    @Test
    fun `manual rate fills a missing ECB rate`() {
        val manual = mapOf("THB" to ManualRate(BigDecimal("0.1"), "ILS"))
        val totals = Totals.all(listOf(bangkok), ils, manual, snapshot)
        assertEquals(200L, totals.grandHomeMinor) // 20.00 THB * 0.1 = 2.00 ILS
        assertEquals(RateSource.MANUAL, totals.trips.single().rate?.source)
        assertEquals(0, totals.missingRateCount)
    }

    @Test
    fun `empty list`() {
        val totals = Totals.all(emptyList(), ils, emptyMap(), null)
        assertEquals(WalletTotals(emptyList(), 0L, 0), totals)
    }
}
