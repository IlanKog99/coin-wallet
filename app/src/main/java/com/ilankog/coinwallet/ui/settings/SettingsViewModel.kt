package com.ilankog.coinwallet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.SettingsSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val home: CurrencyInfo,
    val snapshot: RateSnapshot?,
    val busy: Boolean,
    val lastResult: RefreshResult?,
    val now: Long,
    val version: String,
    val hopEnabled: Boolean = false,
    val hop: CurrencyInfo = Currencies.orFallback(Currencies.FALLBACK),
)

class SettingsViewModel(
    private val rates: RateRepository,
    private val settings: SettingsSource,
    private val version: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(rates.snapshot, rates.busy, rates.lastResult, settings.settings) { snap, busy, last, s ->
            build(snap, busy, last, s)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(rates.snapshot.value, rates.busy.value, rates.lastResult.value, settings.settings.value),
        )

    init {
        viewModelScope.launch { rates.load() }
    }

    fun setHome(code: String) {
        if (Currencies.get(code) != null) settings.setHomeCurrency(code)
    }

    fun setHopEnabled(enabled: Boolean) {
        settings.setHopEnabled(enabled)
    }

    fun setHop(code: String) {
        if (Currencies.get(code) != null) settings.setHopCurrency(code)
    }

    fun refresh() {
        viewModelScope.launch { rates.refresh() }
    }

    private fun build(snap: RateSnapshot?, busy: Boolean, last: RefreshResult?, s: Settings) =
        SettingsUiState(
            Currencies.orFallback(s.homeCurrency), snap, busy, last, clock(), version,
            hopEnabled = s.hopEnabled,
            hop = Currencies.orFallback(s.hopCurrency),
        )
}
