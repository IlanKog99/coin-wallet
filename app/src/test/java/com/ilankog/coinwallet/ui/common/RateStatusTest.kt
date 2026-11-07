package com.ilankog.coinwallet.ui.common

import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.RepoNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateStatusTest {
    private val snap = RateSnapshot(1000L, "2026-11-06", mapOf("EUR" to 1.0))

    @Test
    fun `status derivation`() {
        assertEquals(RateStatus.Updating, rateStatus(snap, busy = true, last = null))
        assertEquals(RateStatus.None, rateStatus(null, busy = false, last = RefreshResult.Offline))
        assertEquals(RateStatus.Fresh(1000L), rateStatus(snap, busy = false, last = null))
        assertEquals(RateStatus.Fresh(1000L), rateStatus(snap, busy = false, last = RefreshResult.Success))
        assertEquals(RateStatus.Offline(1000L), rateStatus(snap, busy = false, last = RefreshResult.HttpError(500)))
    }

    @Test
    fun `labels`() {
        val now = 1000L + 2 * 3_600_000L
        assertEquals("Updating rates…", RateStatus.Updating.label(now))
        assertEquals("No rates yet", RateStatus.None.label(now))
        assertEquals("Rates updated 2h ago", RateStatus.Fresh(1000L).label(now))
        assertEquals("Offline · rates from 2h ago", RateStatus.Offline(1000L).label(now))
    }

    @Test
    fun `messages`() {
        assertNull(RefreshResult.Success.toMessage())
        assertEquals("You're offline. Rates not updated.", RefreshResult.Offline.describe())
        assertEquals("Rate server error (503)", RefreshResult.HttpError(503).describe())
        assertEquals("Rate data was invalid", RefreshResult.BadData.describe())
        assertEquals("Rates updated", RefreshResult.Success.describe())
        assertEquals("Saved trips were damaged and were reset", RepoNotice.TripsReset.text())
        assertEquals("Couldn't save. Changes may be lost.", RepoNotice.SaveFailed.text())
    }

    @Test
    fun `delete notice wording`() {
        val t = Trip("a", "A", "CZK", emptyMap(), createdAt = 0)
        assertEquals("Trip deleted", RepoNotice.Deleted(listOf(t)).text())
        assertEquals("3 trips deleted", RepoNotice.Deleted(listOf(t, t.copy(id = "b"), t.copy(id = "c"))).text())
    }
}
