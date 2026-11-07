package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeNoteTest {
    private val note = ChangeNote("n1", 500, 1L)
    private val t = Trip("t", "T", "CZK", emptyMap(), createdAt = 0, changeDue = listOf(note, ChangeNote("n2", 100, 2L)))

    @Test
    fun `currency locked by notes or counts`() {
        assertFalse(TripRules.canChangeCurrency(t))
        assertFalse(TripRules.canChangeCurrency(t.copy(changeDue = emptyList(), counts = mapOf("c100" to 1))))
        assertTrue(TripRules.canChangeCurrency(t.copy(changeDue = emptyList())))
    }

    @Test
    fun `dismiss removes only that note and counts never touch notes`() {
        assertEquals(listOf(ChangeNote("n2", 100, 2L)), TripRules.dismissNote(t, "n1").changeDue)
        assertEquals(t, TripRules.dismissNote(t, "nope"))
        val more = TripRules.increment(t, Denomination(DenomKind.COIN, 100))
        assertEquals(t.changeDue, more.changeDue)
        assertEquals(t.changeDue, TripRules.reset(more).changeDue)
    }

    @Test
    fun `codec keeps notes, loads old files, rejects bad amounts`() {
        val ok = TripsCodec.decode(TripsCodec.encode(listOf(t))) as DecodeResult.Ok
        assertEquals(t.changeDue, ok.trips.single().changeDue)
        val old = """{"version":1,"trips":[{"id":"a","name":"P","currency":"CZK","counts":{},"createdAt":1}]}"""
        assertEquals(emptyList<ChangeNote>(), (TripsCodec.decode(old) as DecodeResult.Ok).trips.single().changeDue)
        val bad = TripsCodec.encode(listOf(t.copy(changeDue = listOf(ChangeNote("x", 0, 1L)))))
        assertTrue(TripsCodec.decode(bad) is DecodeResult.Corrupt)
    }
}
