package com.ilankog.coinwallet.core

import com.ilankog.coinwallet.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrenciesTest {

    @Test
    fun `supports exactly the Frankfurter currencies`() {
        val api = Regex("\"([A-Z]{3})\":")
            .findAll(Fixtures.text("frankfurter_latest.json"))
            .map { it.groupValues[1] }
            .toSet() + "EUR"
        assertEquals(api, Currencies.all.map { it.code }.toSet())
        assertEquals(30, Currencies.all.size)
    }

    @Test
    fun `denominations are positive ascending and unique`() {
        for (c in Currencies.all) {
            assertTrue(c.code, c.coins.isNotEmpty() && c.notes.isNotEmpty())
            assertEquals(c.code, c.coins.sorted(), c.coins)
            assertEquals(c.code, c.notes.sorted(), c.notes)
            assertTrue(c.code, (c.coins + c.notes).all { it > 0 })
            assertEquals(c.code, c.denominations.size, c.denominations.map { it.key }.toSet().size)
        }
    }

    @Test
    fun `zero decimal currencies`() {
        assertEquals(
            setOf("HUF", "IDR", "ISK", "JPY", "KRW"),
            Currencies.all.filter { it.decimals == 0 }.map { it.code }.toSet(),
        )
        assertEquals(1L, Currencies.orFallback("JPY").minorPerMajor)
        assertEquals(100L, Currencies.orFallback("EUR").minorPerMajor)
    }

    @Test
    fun `coin and note of same value have distinct keys`() {
        val keys = Currencies.orFallback("CNY").denominations.map { it.key }
        assertTrue("c100" in keys)
        assertTrue("n100" in keys)
    }

    @Test
    fun `default home follows locale country`() {
        assertEquals("CZK", Currencies.defaultHome(Locale("cs", "CZ")))
        assertEquals("USD", Currencies.defaultHome(Locale.US))
        assertEquals("ILS", Currencies.defaultHome(Locale("he", "IL")))
    }

    @Test
    fun `default home falls back to EUR`() {
        assertEquals("EUR", Currencies.defaultHome(Locale("ru", "RU")))
        assertEquals("EUR", Currencies.defaultHome(Locale.ENGLISH))
    }

    @Test
    fun `get unknown is null and orFallback gives EUR`() {
        assertNull(Currencies.get("XAU"))
        assertEquals("EUR", Currencies.orFallback("XAU").code)
    }

    @Test
    fun `search matches code or name case-insensitively`() {
        assertEquals(listOf("CZK"), CurrencySearch.filter("czk").map { it.code })
        assertEquals(listOf("CZK"), CurrencySearch.filter(" koruna ").map { it.code })
        assertEquals(30, CurrencySearch.filter("").size)
        assertTrue(CurrencySearch.filter("zzz").isEmpty())
    }

    @Test
    fun `tiers spread over copper brass silver`() {
        val eur = Currencies.orFallback("EUR")
        assertEquals(0, DenomTier.of(Denomination(DenomKind.COIN, 1), eur))
        assertEquals(1, DenomTier.of(Denomination(DenomKind.COIN, 20), eur))
        assertEquals(2, DenomTier.of(Denomination(DenomKind.COIN, 200), eur))
        assertEquals(2, DenomTier.of(Denomination(DenomKind.NOTE, 20000), eur))
    }

    @Test
    fun `face text uses minor for fractions, major for whole, K for large`() {
        val eur = Currencies.orFallback("EUR")
        assertEquals("50", DenomTier.faceText(Denomination(DenomKind.COIN, 50), eur))
        assertEquals("2", DenomTier.faceText(Denomination(DenomKind.COIN, 200), eur))
        val idr = Currencies.orFallback("IDR")
        assertEquals("100K", DenomTier.faceText(Denomination(DenomKind.NOTE, 100000), idr))
        assertEquals("5000", DenomTier.faceText(Denomination(DenomKind.NOTE, 5000), idr))
    }
}
