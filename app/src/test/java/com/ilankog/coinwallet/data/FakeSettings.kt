package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.core.ManualRate
import com.ilankog.coinwallet.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

class FakeSettings(home: String = "ILS") : SettingsSource {
    private val state = MutableStateFlow(Settings(home, emptyMap()))
    override val settings: StateFlow<Settings> = state

    override fun setHomeCurrency(code: String) {
        state.value = state.value.copy(homeCurrency = code)
    }

    override fun setManualRate(currency: String, rate: BigDecimal, home: String) {
        state.value = state.value.copy(manualRates = state.value.manualRates + (currency to ManualRate(rate, home)))
    }

    override fun clearManualRate(currency: String) {
        state.value = state.value.copy(manualRates = state.value.manualRates - currency)
    }

    override fun setHopEnabled(enabled: Boolean) {
        state.value = state.value.copy(hopEnabled = enabled)
    }

    override fun setHopCurrency(code: String) {
        state.value = state.value.copy(hopCurrency = code)
    }
}
