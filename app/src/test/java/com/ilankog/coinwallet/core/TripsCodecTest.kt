package com.ilankog.coinwallet.core

import com.ilankog.coinwallet.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripsCodecTest {
    private val trips = listOf(
        Trip("a", "Prague", "CZK", mapOf("c100" to 3, "n20000" to 1), createdAt = 10),
        Trip("b", "Tokyo", "JPY", emptyMap(), createdAt = 20),
    )

    @Test
    fun `round trip`() {
        assertEquals(DecodeResult.Ok(trips, 0), TripsCodec.decode(TripsCodec.encode(trips)))
    }

    @Test
    fun `truncated file is corrupt`() {
        assertTrue(TripsCodec.decode(Fixtures.text("trips_corrupt.json")) is DecodeResult.Corrupt)
        assertTrue(TripsCodec.decode("not json") is DecodeResult.Corrupt)
    }

    @Test
    fun `wrong version is corrupt`() {
        assertTrue(TripsCodec.decode("""{"version":2,"trips":[]}""") is DecodeResult.Corrupt)
    }

    @Test
    fun `unknown currency is corrupt`() {
        val text = """{"version":1,"trips":[{"id":"a","name":"X","currency":"XAU","createdAt":1}]}"""
        assertTrue(TripsCodec.decode(text) is DecodeResult.Corrupt)
    }

    @Test
    fun `count outside range is corrupt`() {
        for (n in listOf(10000, -1)) {
            val text = """{"version":1,"trips":[{"id":"a","name":"X","currency":"EUR","counts":{"c1":$n},"createdAt":1}]}"""
            assertTrue("$n", TripsCodec.decode(text) is DecodeResult.Corrupt)
        }
    }

    @Test
    fun `unknown denomination keys are dropped`() {
        val text = """{"version":1,"trips":[{"id":"a","name":"X","currency":"EUR","counts":{"c1":2,"c3":4},"createdAt":1}]}"""
        val r = TripsCodec.decode(text) as DecodeResult.Ok
        assertEquals(mapOf("c1" to 2), r.trips.single().counts)
        assertEquals(1, r.droppedKeys)
    }
}
