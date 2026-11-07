package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentTest {
    private val czk = Currencies.orFallback("CZK")
    private val jpy = Currencies.orFallback("JPY")

    private fun coin(v: Long) = Denomination(DenomKind.COIN, v)
    private fun note(v: Long) = Denomination(DenomKind.NOTE, v)
    private fun trip(cur: String, vararg counts: Pair<String, Int>) = Trip("t", "T", cur, mapOf(*counts), createdAt = 0)

    @Test
    fun `exact with small coins`() {
        // 1 Kč x3 + 2 Kč x1, price 5 Kč
        val s = Payment.suggest(trip("CZK", "c100" to 3, "c200" to 1), czk, 500)!!
        assertEquals(listOf(coin(100) to 3, coin(200) to 1), s.pieces)
        assertEquals(500L, s.paidMinor)
        assertEquals(0L, s.changeMinor)
    }

    @Test
    fun `overshoot then drop extras`() {
        // 1 Kč x3 + 10 Kč x1, price 5 Kč -> just the 10
        val s = Payment.suggest(trip("CZK", "c100" to 3, "c1000" to 1), czk, 500)!!
        assertEquals(listOf(coin(1000) to 1), s.pieces)
        assertEquals(500L, s.changeMinor)
    }

    @Test
    fun `insufficient or non-positive price is null`() {
        assertNull(Payment.suggest(trip("CZK", "c100" to 3), czk, 500))
        assertNull(Payment.suggest(trip("CZK", "c100" to 3), czk, 0))
        assertNull(Payment.suggest(trip("CZK", "c100" to 3), czk, -1))
    }

    @Test
    fun `zero decimal yen`() {
        // ¥1 x7 + ¥100 x4, price ¥250: ascending 7 + 300 = 307, drop the seven ¥1 -> 300, change 50
        val s = Payment.suggest(trip("JPY", "c1" to 7, "c100" to 4), jpy, 250)!!
        assertEquals(listOf(coin(100) to 3), s.pieces)
        assertEquals(50L, s.changeMinor)
    }

    @Test
    fun `small coins used when they still matter`() {
        // 1 Kč x3 + 10 Kč x1, price 12 Kč -> ascending 3 + 10 = 13, then one 1 Kč dropped -> exact 12
        val s = Payment.suggest(trip("CZK", "c100" to 3, "c1000" to 1), czk, 1200)!!
        assertEquals(listOf(coin(100) to 2, coin(1000) to 1), s.pieces)
        assertEquals(0L, s.changeMinor)
    }

    @Test
    fun `single large note`() {
        val s = Payment.suggest(trip("CZK", "n10000" to 1), czk, 3000)!!
        assertEquals(listOf(note(10000) to 1), s.pieces)
        assertEquals(7000L, s.changeMinor)
    }

    @Test
    fun `apply subtracts and records change`() {
        val t = trip("CZK", "c100" to 3, "c1000" to 1)
        val s = Payment.suggest(t, czk, 500)!!
        val after = Payment.apply(t, s, "n1", 42L)!!
        assertEquals(mapOf("c100" to 3), after.counts)
        assertEquals(listOf(ChangeNote("n1", 500, 42L)), after.changeDue)
    }

    @Test
    fun `apply without change adds no note`() {
        val t = trip("CZK", "c100" to 3, "c200" to 1)
        val after = Payment.apply(t, Payment.suggest(t, czk, 500)!!, "n1", 1L)!!
        assertEquals(emptyMap<String, Int>(), after.counts)
        assertEquals(emptyList<ChangeNote>(), after.changeDue)
    }

    @Test
    fun `apply rejects a stale suggestion`() {
        val t = trip("CZK", "c1000" to 1)
        val s = Payment.suggest(t, czk, 500)!!
        assertNull(Payment.apply(t.copy(counts = emptyMap()), s, "n1", 1L))
    }
}
