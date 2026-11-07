package com.ilankog.coinwallet.ui.trips

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.MainDispatcherRule
import com.ilankog.coinwallet.core.Staleness
import com.ilankog.coinwallet.data.FakeFetcher
import com.ilankog.coinwallet.data.FakeSettings
import com.ilankog.coinwallet.data.HttpResponse
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.TripRepository
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.common.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val body = Fixtures.text("frankfurter_latest.json")
    private var now = 1_000_000_000L

    private class Env(val vm: TripsViewModel, val trips: TripRepository, val fetcher: FakeFetcher, val settings: FakeSettings)

    private fun TestScope.env(
        fetcher: FakeFetcher = FakeFetcher { HttpResponse(200, body) },
        settings: FakeSettings = FakeSettings("ILS"),
    ): Env {
        val io = UnconfinedTestDispatcher(testScheduler)
        val trips = TripRepository(folder.root, io, clock = { now })
        val rates = RateRepository(folder.root, fetcher, CoroutineScope(io), io, clock = { now })
        return Env(TripsViewModel(trips, rates, settings, clock = { now }), trips, fetcher, settings)
    }

    @Test
    fun `starts loaded and empty with no rates`() = runTest {
        val e = env(FakeFetcher { throw IOException("offline") })
        val s = e.vm.state.value
        assertTrue(s.loaded)
        assertTrue(s.totals.trips.isEmpty())
        assertEquals(RateStatus.None, s.rateStatus)
        assertEquals("ILS", s.home.code)
    }

    @Test
    fun `refreshIfStale fetches only when stale`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        assertEquals(1, e.fetcher.calls)
        assertEquals(RateStatus.Fresh(now), e.vm.state.value.rateStatus)
        e.vm.refreshIfStale()
        assertEquals(1, e.fetcher.calls)
        now += Staleness.MAX_AGE_MS + 1
        e.vm.refreshIfStale()
        assertEquals(2, e.fetcher.calls)
    }

    @Test
    fun `auto refresh failure shows offline status without a message`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        val fetchedAt = now
        e.fetcher.response = { throw IOException("offline") }
        now += Staleness.MAX_AGE_MS + 1
        e.vm.refreshIfStale()
        assertEquals(RateStatus.Offline(fetchedAt), e.vm.state.value.rateStatus)
        assertNull(e.vm.message.value)
    }

    @Test
    fun `manual refresh failure posts a message`() = runTest {
        val e = env(FakeFetcher { HttpResponse(503, "") })
        e.vm.refresh()
        assertEquals(UiMessage.ServerError(503), e.vm.message.value)
        e.vm.messageShown()
        assertNull(e.vm.message.value)
    }

    @Test
    fun `createTrip validates input and totals convert`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        e.vm.createTrip("   ", "CZK")
        e.vm.createTrip("Prague", "XAU")
        assertTrue(e.vm.state.value.totals.trips.isEmpty())
        e.vm.createTrip(" Prague ", "CZK")
        val t = e.trips.trips.value.single()
        assertEquals("Prague", t.name)
        e.trips.update(t.id) { it.copy(counts = mapOf("n10000" to 1)) }
        assertEquals(1467L, e.vm.state.value.totals.trips.single().homeMinor)
        assertEquals(1467L, e.vm.state.value.totals.grandHomeMinor)
    }

    @Test
    fun `home currency change recomputes live`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        e.vm.createTrip("Prague", "CZK")
        val t = e.trips.trips.value.single()
        e.trips.update(t.id) { it.copy(counts = mapOf("n10000" to 1)) }
        e.settings.setHomeCurrency("EUR")
        assertEquals("EUR", e.vm.state.value.home.code)
        assertEquals(411L, e.vm.state.value.totals.grandHomeMinor)
    }

    @Test
    fun `deleteTrips removes all selected and posts one notice`() = runTest {
        val e = env()
        e.vm.createTrip("A", "CZK")
        e.vm.createTrip("B", "EUR")
        e.vm.createTrip("C", "JPY")
        val (a, b, c) = e.trips.trips.value
        e.vm.deleteTrips(setOf(a.id, c.id))
        assertEquals(listOf(b), e.trips.trips.value)
        assertEquals(com.ilankog.coinwallet.data.RepoNotice.Deleted(listOf(a, c)), e.trips.notice.value)
        e.vm.deleteTrips(emptySet())
        assertEquals(listOf(b), e.trips.trips.value)
    }
}
