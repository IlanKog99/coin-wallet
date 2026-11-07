package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TripRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var ids = 0
    private val two = Denomination(DenomKind.COIN, 200)

    private fun TestScope.repo(dir: File = folder.root) =
        TripRepository(dir, io = UnconfinedTestDispatcher(testScheduler), newId = { "id${++ids}" }, clock = { 1000L })

    @Test
    fun `missing file loads empty without notice`() = runTest {
        val r = repo()
        r.load()
        assertEquals(emptyList<Trip>(), r.trips.value)
        assertTrue(r.isLoaded.value)
        assertNull(r.notice.value)
    }

    @Test
    fun `create persists across instances`() = runTest {
        val t = repo().create("Prague", "CZK")
        assertEquals(Trip("id1", "Prague", "CZK", emptyMap(), 1000L), t)
        val again = repo()
        again.load()
        assertEquals(listOf(t), again.trips.value)
    }

    @Test
    fun `update persists and unknown id returns null`() = runTest {
        val r = repo()
        val t = r.create("Prague", "CZK")
        val updated = r.update(t.id) { TripRules.increment(it, two) }
        assertEquals(1, updated?.let { TripRules.count(it, two) })
        assertNull(r.update("nope") { it })
        val again = repo()
        again.load()
        assertEquals(1, TripRules.count(again.trips.value.single(), two))
    }

    @Test
    fun `delete posts notice and restore brings the same trip back`() = runTest {
        val r = repo()
        val t = r.create("Prague", "CZK")
        assertEquals(t, r.delete(t.id))
        assertTrue(r.trips.value.isEmpty())
        assertEquals(RepoNotice.Deleted(t), r.notice.value)
        r.clearNotice(RepoNotice.SaveFailed)
        assertEquals(RepoNotice.Deleted(t), r.notice.value) // other notice not cleared
        r.clearNotice(RepoNotice.Deleted(t))
        assertNull(r.notice.value)
        r.restore(t)
        r.restore(t) // idempotent
        assertEquals(listOf(t), r.trips.value)
        val again = repo()
        again.load()
        assertEquals(listOf(t), again.trips.value)
    }

    @Test
    fun `corrupt file is moved aside and reported`() = runTest {
        val text = Fixtures.text("trips_corrupt.json")
        File(folder.root, "trips.json").writeText(text)
        val r = repo()
        r.load()
        assertTrue(r.trips.value.isEmpty())
        assertEquals(RepoNotice.TripsReset, r.notice.value)
        assertEquals(text, File(folder.root, "trips.corrupt.json").readText())
        assertFalse(File(folder.root, "trips.json").exists())
    }

    @Test
    fun `save failure is reported and memory state kept`() = runTest {
        val r = repo(File(folder.root, "missing-dir"))
        val t = r.create("Oslo", "NOK")
        assertEquals(listOf(t), r.trips.value)
        assertEquals(RepoNotice.SaveFailed, r.notice.value)
    }

    @Test
    fun `parallel increments are all applied`() = runTest {
        val r = repo()
        val t = r.create("Prague", "CZK")
        (1..50).map { launch { r.update(t.id) { TripRules.increment(it, two) } } }.joinAll()
        assertEquals(50, TripRules.count(r.trips.value.single(), two))
        val again = repo()
        again.load()
        assertEquals(50, TripRules.count(again.trips.value.single(), two))
    }
}
