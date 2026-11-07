package com.ilankog.coinwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.core.Totals
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.core.TripSummary
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.SettingsSource
import com.ilankog.coinwallet.data.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class WalletUiState(
    val summary: TripSummary,
    val home: CurrencyInfo,
    val rateDate: String?,
    val hasManualRate: Boolean,
)

sealed interface WalletState {
    data object Loading : WalletState
    data object NotFound : WalletState
    data class Ready(val ui: WalletUiState) : WalletState
}

class WalletViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val rates: RateRepository,
    private val settings: SettingsSource,
) : ViewModel() {

    val state: StateFlow<WalletState> =
        combine(trips.trips, trips.isLoaded, rates.snapshot, settings.settings) { list, loaded, snap, s ->
            build(list, loaded, snap, s)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(trips.trips.value, trips.isLoaded.value, rates.snapshot.value, settings.settings.value),
        )

    init {
        viewModelScope.launch { trips.load() }
        viewModelScope.launch { rates.load() }
    }

    fun increment(d: Denomination): Unit = edit { TripRules.increment(it, d) }
    fun decrement(d: Denomination): Unit = edit { TripRules.decrement(it, d) }
    fun setCount(d: Denomination, value: Int): Unit = edit { TripRules.setCount(it, d, value) }
    fun reset(): Unit = edit { TripRules.reset(it) }

    fun delete() {
        viewModelScope.launch { trips.delete(setOf(tripId)) }
    }

    fun setManualRate(rate: BigDecimal) {
        val ui = ready() ?: return
        settings.setManualRate(ui.summary.info.code, rate, ui.home.code)
    }

    fun clearManualRate() {
        val ui = ready() ?: return
        settings.clearManualRate(ui.summary.info.code)
    }

    /** Renames; changes currency only when no pieces are counted (spec 7.2). */
    fun editTrip(name: String, currency: String) {
        val clean = TripRules.cleanName(name) ?: return
        if (Currencies.get(currency) == null) return
        edit { t ->
            if (currency != t.currency && TripRules.hasCounts(t)) t.copy(name = clean) else t.copy(name = clean, currency = currency)
        }
    }

    private fun ready(): WalletUiState? = (state.value as? WalletState.Ready)?.ui

    private fun edit(transform: (Trip) -> Trip) {
        viewModelScope.launch { trips.update(tripId, transform) }
    }

    private fun build(list: List<Trip>, loaded: Boolean, snap: RateSnapshot?, s: Settings): WalletState {
        if (!loaded) return WalletState.Loading
        val trip = list.firstOrNull { it.id == tripId } ?: return WalletState.NotFound
        val info = Currencies.get(trip.currency) ?: return WalletState.NotFound
        val home = Currencies.orFallback(s.homeCurrency)
        val manual = s.manualRates[trip.currency]
        return WalletState.Ready(
            WalletUiState(
                summary = Totals.summarize(trip, info, home, manual, snap),
                home = home,
                rateDate = snap?.date,
                hasManualRate = manual != null && manual.home == home.code,
            ),
        )
    }
}
