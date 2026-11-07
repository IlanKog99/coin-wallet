package com.ilankog.coinwallet.ui.wallet

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.MainDispatcherRule
import com.ilankog.coinwallet.core.ChangeNote
import com.ilankog.coinwallet.core.Conversion
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.PaySuggestion
import com.ilankog.coinwallet.core.Payment
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.data.FakeFetcher
import com.ilankog.coinwallet.data.FakeSettings
import com.ilankog.coinwallet.data.HttpResponse
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RepoNotice
import com.ilankog.coinwallet.data.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val body = Fixtures.text("frankfurter_latest.json")
    private val tenKc = Denomination(DenomKind.COIN, 1000)
    private val hundredKc = Denomination(DenomKind.NOTE, 10000)

    private class Env(val vm: WalletViewModel, val trips: TripRepository)

    private suspend fun TestScope.env(settings: FakeSettings = FakeSettings("ILS"), id: String = "trip"): Env {
        val io = UnconfinedTestDispatcher(testScheduler)
        val trips = TripRepository(folder.root, io, newId = { "trip" }, clock = { 1L })
        val rates = RateRepository(folder.root, FakeFetcher { HttpResponse(200, body) }, CoroutineScope(io), io, clock = { 1L })
        rates.refresh()
        trips.create("Prague", "CZK")
        return Env(WalletViewModel(id, trips, rates, settings, newId = { "n1" }, clock = { 42L }), trips)
    }

    private fun WalletViewModel.ui() = (state.value as WalletState.Ready).ui

    @Test
    fun `unknown trip is NotFound`() = runTest {
        assertEquals(WalletState.NotFound, env(id = "nope").vm.state.value)
    }

    @Test
    fun `increment decrement set and reset`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        vm.increment(tenKc)
        vm.decrement(tenKc)
        assertEquals(1, TripRules.count(vm.ui().summary.trip, tenKc))
        vm.decrement(hundredKc) // already 0: no-op
        assertEquals(0, TripRules.count(vm.ui().summary.trip, hundredKc))
        vm.setCount(tenKc, 25)
        assertEquals(25000L, vm.ui().summary.totalMinor)
        vm.reset()
        assertEquals(0L, vm.ui().summary.totalMinor)
    }

    @Test
    fun `ECB conversion with rate date`() = runTest {
        val vm = env().vm
        vm.setCount(hundredKc, 1)
        val ui = vm.ui()
        assertEquals(1467L, ui.summary.homeMinor)
        assertEquals(RateSource.ECB, ui.summary.rate?.source)
        assertEquals("2026-11-06", ui.rateDate)
        assertFalse(ui.hasManualRate)
    }

    @Test
    fun `manual rate overrides then clears`() = runTest {
        val vm = env().vm
        vm.setCount(hundredKc, 1)
        vm.setManualRate(BigDecimal("0.2"))
        assertEquals(RateSource.MANUAL, vm.ui().summary.rate?.source)
        assertEquals(2000L, vm.ui().summary.homeMinor)
        assertTrue(vm.ui().hasManualRate)
        vm.clearManualRate()
        assertEquals(RateSource.ECB, vm.ui().summary.rate?.source)
    }

    @Test
    fun `same currency as home uses rate one`() = runTest {
        val vm = env(FakeSettings("CZK")).vm
        vm.setCount(hundredKc, 2)
        assertEquals(RateSource.SAME, vm.ui().summary.rate?.source)
        assertEquals(20000L, vm.ui().summary.homeMinor)
    }

    @Test
    fun `delete leads to NotFound and posts notice`() = runTest {
        val e = env()
        e.vm.delete()
        assertEquals(WalletState.NotFound, e.vm.state.value)
        assertTrue(e.trips.notice.value is RepoNotice.Deleted)
    }

    @Test
    fun `edit renames and blocks currency change while counts exist`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        vm.editTrip(" Brno ", "EUR")
        assertEquals("Brno", vm.ui().summary.trip.name)
        assertEquals("CZK", vm.ui().summary.trip.currency)
        vm.reset()
        vm.editTrip("Brno", "EUR")
        assertEquals("EUR", vm.ui().summary.trip.currency)
        vm.editTrip("  ", "EUR")
        vm.editTrip("Brno", "XAU")
        assertEquals("Brno", vm.ui().summary.trip.name)
        assertEquals("EUR", vm.ui().summary.trip.currency)
    }

    private fun TestScope.paid(vm: WalletViewModel, s: PaySuggestion): Boolean? {
        var result: Boolean? = null
        vm.pay(s) { result = it }
        return result
    }

    @Test
    fun `pay removes pieces and adds a change note with clock and id`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        val s = Payment.suggest(vm.ui().summary.trip, vm.ui().summary.info, 300)!!
        assertEquals(true, paid(vm, s))
        assertEquals(0, TripRules.count(vm.ui().summary.trip, tenKc))
        assertEquals(listOf(ChangeNote("n1", 700, 42L)), vm.ui().summary.trip.changeDue)
    }

    @Test
    fun `pay with a stale suggestion changes nothing`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        val s = Payment.suggest(vm.ui().summary.trip, vm.ui().summary.info, 300)!!
        vm.decrement(tenKc)
        val before = vm.ui().summary.trip
        assertEquals(false, paid(vm, s))
        assertEquals(before, vm.ui().summary.trip)
    }

    @Test
    fun `dismissNote removes only that note`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        vm.increment(tenKc)
        val info = vm.ui().summary.info
        paid(vm, Payment.suggest(vm.ui().summary.trip, info, 300)!!)
        vm.dismissNote("missing")
        assertEquals(1, vm.ui().summary.trip.changeDue.size)
        vm.dismissNote("n1")
        assertTrue(vm.ui().summary.trip.changeDue.isEmpty())
    }

    @Test
    fun `hop converts with ECB and hides when off or same as trip`() = runTest {
        val settings = FakeSettings("ILS")
        val vm = env(settings).vm
        vm.increment(tenKc)
        assertNull(vm.ui().hop)
        settings.setHopEnabled(true)
        val hop = vm.ui().hop!!
        assertEquals("EUR", hop.info.code)
        assertEquals(41L, hop.minor) // 10 CZK / 24.35
        settings.setHopCurrency("CZK")
        assertNull(vm.ui().hop)
    }

    @Test
    fun `currency stays locked while change notes exist`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        paid(vm, Payment.suggest(vm.ui().summary.trip, vm.ui().summary.info, 300)!!)
        vm.editTrip("Prague", "EUR")
        assertEquals("CZK", vm.ui().summary.trip.currency)
        vm.dismissNote("n1")
        vm.editTrip("Prague", "EUR")
        assertEquals("EUR", vm.ui().summary.trip.currency)
    }

    @Test
    fun `payPreview covers empty invalid insufficient and ok`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        val ui = vm.ui()
        assertEquals(PayPreview.Empty, payPreview(ui, " "))
        assertEquals(PayPreview.Invalid, payPreview(ui, "abc"))
        assertEquals(PayPreview.Invalid, payPreview(ui, "0"))
        assertEquals(PayPreview.Insufficient(1000), payPreview(ui, "50"))
        val ok = payPreview(ui, "3") as PayPreview.Ok
        assertEquals(700L, ok.s.changeMinor)
        assertEquals(Conversion.convert(300, ui.summary.info, ui.home, ui.summary.rate!!.rate), ok.homeMinor)
        assertNull(ok.hopMinor)
    }

    @Test
    fun `noteTime formats in the given zone`() {
        assertEquals("Jan 1, 1970, 12:00\u202FAM", noteTime(0, java.time.ZoneId.of("UTC"), java.util.Locale.US))
    }
}
