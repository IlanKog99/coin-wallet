package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SettingsCodecTest {
    @Test
    fun `defaults when empty`() {
        assertEquals(Settings("ILS", emptyMap()), SettingsCodec.decode(emptyMap<String, Any>(), "ILS"))
    }

    @Test
    fun `reads home and manual rates`() {
        val s = SettingsCodec.decode(
            mapOf(SettingsCodec.KEY_HOME to "CZK", SettingsCodec.rateKey("USD") to "3.6", SettingsCodec.homeKey("USD") to "ILS"),
            "EUR",
        )
        assertEquals("CZK", s.homeCurrency)
        assertEquals(mapOf("USD" to ManualRate(BigDecimal("3.6"), "ILS")), s.manualRates)
    }

    @Test
    fun `ignores invalid entries`() {
        val s = SettingsCodec.decode(
            mapOf(
                SettingsCodec.KEY_HOME to "XXX",
                "manual_rate_USD" to "abc", "manual_home_USD" to "ILS",
                "manual_rate_GBP" to "1.2",
                "manual_rate_XAU" to "2", "manual_home_XAU" to "ILS",
                "manual_rate_JPY" to 5, "manual_home_JPY" to "ILS",
                "manual_rate_THB" to "0.1", "manual_home_THB" to "ZZZ",
            ),
            "EUR",
        )
        assertEquals(Settings("EUR", emptyMap()), s)
    }

    @Test
    fun `key names`() {
        assertEquals("manual_rate_USD", SettingsCodec.rateKey("USD"))
        assertEquals("manual_home_USD", SettingsCodec.homeKey("USD"))
    }

    @Test
    fun `hop currency settings`() {
        val off = SettingsCodec.decode(emptyMap<String, Any>(), "ILS")
        assertEquals(false, off.hopEnabled)
        assertEquals("EUR", off.hopCurrency)
        assertEquals(null, off.hop)
        val on = SettingsCodec.decode(mapOf(SettingsCodec.KEY_HOP_ENABLED to true, SettingsCodec.KEY_HOP to "CZK"), "ILS")
        assertEquals("CZK", on.hop)
        val bad = SettingsCodec.decode(mapOf(SettingsCodec.KEY_HOP_ENABLED to true, SettingsCodec.KEY_HOP to "XAU"), "ILS")
        assertEquals("EUR", bad.hop)
    }
}
