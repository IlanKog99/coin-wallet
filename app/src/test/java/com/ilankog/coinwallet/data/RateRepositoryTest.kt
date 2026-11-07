package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.Fixtures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RateRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val body = Fixtures.text("frankfurter_latest.json")

    private fun TestScope.repo(fetcher: HttpFetcher, io: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)) =
        RateRepository(folder.root, fetcher, backgroundScope, io, clock = { 5000L })

    @Test
    fun `success stores snapshot and cache`() = runTest {
        val f = FakeFetcher { HttpResponse(200, body) }
        val r = repo(f)
        assertEquals(RefreshResult.Success, r.refresh())
        val s = requireNotNull(r.snapshot.value)
        assertEquals("2026-11-06", s.date)
        assertEquals(5000L, s.fetchedAt)
        assertEquals(24.35, s.rates.getValue("CZK"), 0.0)
        assertFalse(r.busy.value)
        assertEquals(RefreshResult.Success, r.lastResult.value)
        assertEquals(RateRepository.URL, f.lastUrl)
        val again = repo(f)
        again.load()
        assertEquals(s, again.snapshot.value)
    }

    @Test
    fun `offline keeps previous snapshot`() = runTest {
        val f = FakeFetcher { HttpResponse(200, body) }
        val r = repo(f)
        r.refresh()
        val before = r.snapshot.value
        var busyDuringFetch = false
        f.response = { busyDuringFetch = r.busy.value; throw IOException("offline") }
        assertEquals(RefreshResult.Offline, r.refresh())
        assertTrue(busyDuringFetch)
        assertFalse(r.busy.value)
        assertEquals(before, r.snapshot.value)
        assertEquals(RefreshResult.Offline, r.lastResult.value)
    }

    @Test
    fun `http error and bad data are reported`() = runTest {
        val f = FakeFetcher { HttpResponse(503, "busy") }
        val r = repo(f)
        assertEquals(RefreshResult.HttpError(503), r.refresh())
        f.response = { HttpResponse(200, "<html>") }
        assertEquals(RefreshResult.BadData, r.refresh())
        assertNull(r.snapshot.value)
    }

    @Test
    fun `cached implausible rates are dropped on load`() = runTest {
        File(folder.root, RateRepository.FILE).writeText(
            """{"fetchedAt":1,"date":"2026-11-06","rates":{"EUR":1.0,"CZK":24.35,"JPY":1e-15}}""",
        )
        val r = repo(FakeFetcher { HttpResponse(200, body) })
        r.load()
        assertEquals(setOf("EUR", "CZK"), r.snapshot.value?.rates?.keys)
    }

    @Test
    fun `corrupt cache is deleted on load`() = runTest {
        val cache = File(folder.root, RateRepository.FILE)
        cache.writeText("{oops")
        val r = repo(FakeFetcher { HttpResponse(200, body) })
        r.load()
        assertNull(r.snapshot.value)
        assertFalse(cache.exists())
    }

    @Test
    fun `concurrent refreshes share one request`() = runTest {
        val f = FakeFetcher { HttpResponse(200, body) }
        val r = repo(f, StandardTestDispatcher(testScheduler))
        val a = async { r.refresh() }
        val b = async { r.refresh() }
        assertEquals(RefreshResult.Success, a.await())
        assertEquals(RefreshResult.Success, b.await())
        assertEquals(1, f.calls)
    }

    @Test
    fun `cancelled caller does not leave busy stuck`() = runTest {
        val gate = CountDownLatch(1)
        val f = FakeFetcher { gate.await(5, TimeUnit.SECONDS); HttpResponse(200, body) }
        val r = RateRepository(folder.root, f, backgroundScope, Dispatchers.IO, clock = { 5000L })
        val caller = launch { r.refresh() }
        withContext(Dispatchers.Default) { withTimeout(5000) { r.busy.first { it } } }
        caller.cancelAndJoin()
        gate.countDown()
        withContext(Dispatchers.Default) { withTimeout(5000) { r.busy.first { !it } } }
        assertNotNull(r.snapshot.value)
    }
}
