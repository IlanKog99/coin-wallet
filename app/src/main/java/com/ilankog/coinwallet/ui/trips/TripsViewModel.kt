package com.ilankog.coinwallet.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.core.Staleness
import com.ilankog.coinwallet.core.Totals
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.core.WalletTotals
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.SettingsSource
import com.ilankog.coinwallet.data.TripRepository
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.common.UiMessage
import com.ilankog.coinwallet.ui.common.rateStatus
import com.ilankog.coinwallet.ui.common.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripsUiState(
    val loaded: Boolean,
    val home: CurrencyInfo,
    val totals: WalletTotals,
    val rateStatus: RateStatus,
    val now: Long,
)

class TripsViewModel(
    private val trips: TripRepository,
    private val rates: RateRepository,
    private val settings: SettingsSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val rateFlow = combine(rates.snapshot, rates.busy, rates.lastResult) { s, b, l -> Triple(s, b, l) }

    val state: StateFlow<TripsUiState> =
        combine(trips.trips, trips.isLoaded, rateFlow, settings.settings) { list, loaded, (snap, busy, last), s ->
            build(list, loaded, snap, busy, last, s)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(trips.trips.value, trips.isLoaded.value, rates.snapshot.value, rates.busy.value, rates.lastResult.value, settings.settings.value),
        )

    init {
        viewModelScope.launch { trips.load() }
    }

    /** Called on every screen start; errors only show in the rate chip. */
    fun refreshIfStale() {
        viewModelScope.launch {
            rates.load()
            if (Staleness.isStale(rates.snapshot.value, clock())) rates.refresh()
        }
    }

    /** Pull-to-refresh: failures also show a snackbar. */
    fun refresh() {
        viewModelScope.launch { rates.refresh().toMessage()?.let { _message.value = it } }
    }

    fun messageShown() {
        _message.value = null
    }

    fun createTrip(name: String, currency: String) {
        val clean = TripRules.cleanName(name) ?: return
        if (Currencies.get(currency) == null) return
        viewModelScope.launch { trips.create(clean, currency) }
    }

    fun deleteTrips(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { trips.delete(ids) }
    }

    private fun build(
        list: List<Trip>, loaded: Boolean, snap: RateSnapshot?, busy: Boolean, last: RefreshResult?, s: Settings,
    ): TripsUiState {
        val home = Currencies.orFallback(s.homeCurrency)
        // ponytail: "now" refreshes on any state change, not on a timer; fine for "2h ago" granularity.
        return TripsUiState(loaded, home, Totals.all(list, home, s.manualRates, snap), rateStatus(snap, busy, last), clock())
    }
}
