package com.ilankog.coinwallet.data

import android.content.SharedPreferences
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.core.SettingsCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

interface SettingsSource {
    val settings: StateFlow<Settings>
    fun setHomeCurrency(code: String)
    fun setManualRate(currency: String, rate: BigDecimal, home: String)
    fun clearManualRate(currency: String)
}

class SettingsStore(private val prefs: SharedPreferences, private val defaultHome: String) : SettingsSource {
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<Settings> = _settings.asStateFlow()

    // Held as a field on purpose: SharedPreferences keeps listeners only weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read(): Settings = SettingsCodec.decode(prefs.all, defaultHome)

    override fun setHomeCurrency(code: String) {
        prefs.edit().putString(SettingsCodec.KEY_HOME, code).apply()
    }

    override fun setManualRate(currency: String, rate: BigDecimal, home: String) {
        prefs.edit()
            .putString(SettingsCodec.rateKey(currency), rate.toPlainString())
            .putString(SettingsCodec.homeKey(currency), home)
            .apply()
    }

    override fun clearManualRate(currency: String) {
        prefs.edit().remove(SettingsCodec.rateKey(currency)).remove(SettingsCodec.homeKey(currency)).apply()
    }
}
