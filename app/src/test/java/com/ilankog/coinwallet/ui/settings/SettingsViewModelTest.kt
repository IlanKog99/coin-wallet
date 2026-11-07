package com.ilankog.coinwallet.ui.settings

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.MainDispatcherRule
import com.ilankog.coinwallet.data.FakeFetcher
import com.ilankog.coinwallet.data.FakeSettings
import com.ilankog.coinwallet.data.HttpResponse
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RefreshResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.vm(fetcher: FakeFetcher): SettingsViewModel {
        val io = UnconfinedTestDispatcher(testScheduler)
        val rates = RateRepository(folder.root, fetcher, CoroutineScope(io), io, clock = { 7L })
        return SettingsViewModel(rates, FakeSettings("ILS"), "1.0.0", clock = { 7L })
    }

    @Test
    fun `setHome accepts only supported codes`() = runTest {
        val vm = vm(FakeFetcher { HttpResponse(200, "") })
        vm.setHome("XAU")
        assertEquals("ILS", vm.state.value.home.code)
        vm.setHome("CZK")
        assertEquals("CZK", vm.state.value.home.code)
        assertEquals("1.0.0", vm.state.value.version)
    }

    @Test
    fun `refresh success and failure`() = runTest {
        val f = FakeFetcher { HttpResponse(200, Fixtures.text("frankfurter_latest.json")) }
        val vm = vm(f)
        vm.refresh()
        assertNotNull(vm.state.value.snapshot)
        assertEquals(RefreshResult.Success, vm.state.value.lastResult)
        assertFalse(vm.state.value.busy)
        f.response = { HttpResponse(500, "") }
        vm.refresh()
        assertEquals(RefreshResult.HttpError(500), vm.state.value.lastResult)
        assertNotNull(vm.state.value.snapshot)
    }
}
