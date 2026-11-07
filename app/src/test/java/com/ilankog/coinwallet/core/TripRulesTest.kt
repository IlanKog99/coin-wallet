package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRulesTest {
    private val eur = Currencies.orFallback("EUR")
    private val two = Denomination(DenomKind.COIN, 200)
    private val fiveCent = Denomination(DenomKind.COIN, 5)
    private val twenty = Denomination(DenomKind.NOTE, 2000)
    private val trip = Trip(id = "t", name = "Rome", currency = "EUR", createdAt = 1)

    @Test
    fun `increment and decrement change one denomination`() {
        val t = TripRules.increment(TripRules.increment(trip, two), two)
        assertEquals(2, TripRules.count(t, two))
        assertEquals(1, TripRules.count(TripRules.decrement(t, two), two))
        assertEquals(0, TripRules.count(t, fiveCent))
    }

    @Test
    fun `decrement at zero is a no-op and zero counts are removed`() {
        assertEquals(trip, TripRules.decrement(trip, two))
        val back = TripRules.decrement(TripRules.increment(trip, two), two)
        assertTrue(back.counts.isEmpty())
    }

    @Test
    fun `increment at max is a no-op`() {
        val full = TripRules.setCount(trip, two, 9999)
        assertEquals(full, TripRules.increment(full, two))
    }

    @Test
    fun `setCount clamps to range`() {
        assertEquals(9999, TripRules.count(TripRules.setCount(trip, two, 20000), two))
        assertEquals(0, TripRules.count(TripRules.setCount(trip, two, -3), two))
    }

    @Test
    fun `total sums value times count`() {
        var t = TripRules.setCount(trip, two, 2)
        t = TripRules.setCount(t, fiveCent, 3)
        t = TripRules.setCount(t, twenty, 1)
        assertEquals(400L + 15L + 2000L, TripRules.totalMinor(t, eur))
        assertTrue(TripRules.hasCounts(t))
        assertFalse(TripRules.hasCounts(TripRules.reset(t)))
    }

    @Test
    fun `coin and note of same value are counted separately`() {
        val cny = Currencies.orFallback("CNY")
        val coin = Denomination(DenomKind.COIN, 100)
        val note = Denomination(DenomKind.NOTE, 100)
        val t = TripRules.setCount(TripRules.setCount(trip.copy(currency = "CNY"), coin, 2), note, 3)
        assertEquals(2, TripRules.count(t, coin))
        assertEquals(3, TripRules.count(t, note))
        assertEquals(500L, TripRules.totalMinor(t, cny))
    }

    @Test
    fun `parseCount accepts digits 0 to 9999`() {
        assertEquals(0, TripRules.parseCount(""))
        assertEquals(0, TripRules.parseCount("0"))
        assertEquals(12, TripRules.parseCount(" 0012 "))
        assertEquals(9999, TripRules.parseCount("9999"))
    }

    @Test
    fun `parseCount rejects bad input`() {
        for (bad in listOf("10000", "-1", "1.5", "abc", "1 2", "99999999999")) assertNull(bad, TripRules.parseCount(bad))
    }

    @Test
    fun `cleanName trims and enforces length`() {
        assertEquals("Prague", TripRules.cleanName("  Prague "))
        assertNull(TripRules.cleanName("   "))
        assertEquals(40, TripRules.cleanName("x".repeat(40))?.length)
        assertNull(TripRules.cleanName("x".repeat(41)))
    }
}
