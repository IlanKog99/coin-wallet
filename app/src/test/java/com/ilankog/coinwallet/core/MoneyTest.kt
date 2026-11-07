package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyTest {
    private val eur = Currencies.orFallback("EUR")
    private val jpy = Currencies.orFallback("JPY")
    private val czk = Currencies.orFallback("CZK")
    private val chf = Currencies.orFallback("CHF")

    @Test
    fun `format uses symbol position and currency decimals`() {
        assertEquals("€1,234.50", Money.format(123450, eur, Locale.US))
        assertEquals("¥1,500", Money.format(1500, jpy, Locale.US))
        assertEquals("123.45 Kč", Money.format(12345, czk, Locale.US))
        assertEquals("CHF 5.00", Money.format(500, chf, Locale.US))
        assertEquals("€0.00", Money.format(0, eur, Locale.US))
    }

    @Test
    fun `format follows locale separators`() {
        assertEquals("€1.234,50", Money.format(123450, eur, Locale.GERMANY))
    }

    @Test
    fun `label drops decimals for whole values`() {
        assertEquals("€0.50", Money.label(50, eur, Locale.US))
        assertEquals("€2", Money.label(200, eur, Locale.US))
        assertEquals("¥500", Money.label(500, jpy, Locale.US))
        assertEquals("5,000 Kč", Money.label(500000, czk, Locale.US))
    }

    @Test
    fun `parseRate accepts dot or comma decimals`() {
        assertEquals(BigDecimal("0.25"), Money.parseRate("0,25"))
        assertEquals(BigDecimal("3.6"), Money.parseRate(" 3.6 "))
        assertEquals(BigDecimal("0.5"), Money.parseRate(".5"))
        assertEquals(BigDecimal("1000000"), Money.parseRate("1000000"))
    }

    @Test
    fun `parseRate rejects invalid input`() {
        for (bad in listOf("", "abc", "0", "0.000", "-1", "1e3", "1000000.01", "1.2.3", ".", "1,2.3")) {
            assertNull(bad, Money.parseRate(bad))
        }
    }

    @Test
    fun `formatRate keeps five significant digits`() {
        assertEquals("0.14668", Money.formatRate(BigDecimal("0.1466817248")))
        assertEquals("176.77", Money.formatRate(BigDecimal("176.77")))
        assertEquals("3.6", Money.formatRate(BigDecimal("3.6000")))
        assertEquals("0.000049483", Money.formatRate(BigDecimal("0.0000494830")))
    }

    @Test
    fun `relative time buckets`() {
        val now = 10_000_000_000L
        assertEquals("just now", RelativeTime.ago(now - 30_000, now))
        assertEquals("5 min ago", RelativeTime.ago(now - 5 * 60_000, now))
        assertEquals("2h ago", RelativeTime.ago(now - 2 * 3_600_000 - 1, now))
        assertEquals("3d ago", RelativeTime.ago(now - 3 * 86_400_000L, now))
        assertEquals("just now", RelativeTime.ago(now + 60_000, now))
    }
}
