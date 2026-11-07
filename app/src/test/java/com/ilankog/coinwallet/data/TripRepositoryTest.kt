package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import kotlin.coroutines.CoroutineContext

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
        assertEquals(listOf(t), r.delete(setOf(t.id)))
        assertTrue(r.trips.value.isEmpty())
        assertEquals(RepoNotice.Deleted(listOf(t)), r.notice.value)
        r.clearNotice(RepoNotice.SaveFailed)
        assertEquals(RepoNotice.Deleted(listOf(t)), r.notice.value) // other notice not cleared
        r.clearNotice(RepoNotice.Deleted(listOf(t)))
        assertNull(r.notice.value)
        r.restore(listOf(t))
        r.restore(listOf(t)) // idempotent
        assertEquals(listOf(t), r.trips.value)
        val again = repo()
        again.load()
        assertEquals(listOf(t), again.trips.value)
    }

    @Test
    fun `bulk delete removes several and one undo restores only missing ones`() = runTest {
        val r = repo()
        val a = r.create("A", "CZK")
        val b = r.create("B", "EUR")
        val c = r.create("C", "JPY")
        assertEquals(listOf(a, c), r.delete(setOf(a.id, c.id, "nope")))
        assertEquals(listOf(b), r.trips.value)
        assertEquals(RepoNotice.Deleted(listOf(a, c)), r.notice.value)
        r.restore(listOf(a, b, c))
        assertEquals(setOf(a, b, c), r.trips.value.toSet())
        assertEquals(3, r.trips.value.size)
        val again = repo()
        again.load()
        assertEquals(setOf(a, b, c), again.trips.value.toSet())
    }

    @Test
    fun `deletes before the notice is cleared merge into one Undo`() = runTest {
        val r = repo()
        val a = r.create("A", "CZK")
        val b = r.create("B", "EUR")
        r.delete(setOf(a.id))
        r.delete(setOf(b.id))
        assertEquals(RepoNotice.Deleted(listOf(a, b)), r.notice.value)
        r.clearNotice(RepoNotice.Deleted(listOf(a, b)))
        val c = r.create("C", "JPY")
        r.delete(setOf(c.id))
        assertEquals(RepoNotice.Deleted(listOf(c)), r.notice.value)
    }

    @Test
    fun `deleting unknown ids changes nothing`() = runTest {
        val r = repo()
        r.create("A", "CZK")
        assertEquals(emptyList<Trip>(), r.delete(setOf("nope")))
        assertNull(r.notice.value)
        assertEquals(1, r.trips.value.size)
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
    fun `parallel increments are all applied`() = runBlocking(Dispatchers.Default) {
        val r = TripRepository(folder.root, io = Dispatchers.IO, newId = { "id1" }, clock = { 1000L })
        val t = r.create("Prague", "CZK")
        // Thread.sleep widens the read-modify-write window so a missing Mutex loses updates.
        (1..50).map { launch { r.update(t.id) { Thread.sleep(1); TripRules.increment(it, two) } } }.joinAll()
        assertEquals(50, TripRules.count(r.trips.value.single(), two))
        val again = TripRepository(folder.root, io = Dispatchers.IO)
        again.load()
        assertEquals(50, TripRules.count(again.trips.value.single(), two))
    }

    @Test
    fun `failed delete save shows SaveFailed not Deleted`() = runTest {
        val r = repo(File(folder.root, "missing-dir"))
        val t = r.create("Oslo", "NOK")
        r.clearNotice(RepoNotice.SaveFailed)
        r.delete(setOf(t.id))
        assertEquals(RepoNotice.SaveFailed, r.notice.value)
    }

    @Test
    fun `cancelled first load does not lose trips on next create`() = runTest {
        val existing = repo().create("Prague", "CZK")
        val gate = GatedDispatcher()
        val r = TripRepository(folder.root, io = gate, newId = { "id9" }, clock = { 1000L })
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { r.load() }
        job.cancel() // read is queued on the gated io, never ran
        gate.open()
        r.create("Oslo", "NOK")
        assertEquals(listOf(existing.id, "id9"), r.trips.value.map { it.id })
        val again = repo()
        again.load()
        assertEquals(listOf(existing.id, "id9"), again.trips.value.map { it.id })
    }

    private class GatedDispatcher : CoroutineDispatcher() {
        private var open = false
        private val queue = ArrayDeque<Runnable>()
        fun open() { open = true; while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (open) block.run() else queue.addLast(block)
        }
    }
}
