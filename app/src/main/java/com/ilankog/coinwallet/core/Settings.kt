package com.ilankog.coinwallet.core

data class Settings(
    val homeCurrency: String,
    val manualRates: Map<String, ManualRate>,
    val hopEnabled: Boolean = false,
    val hopCurrency: String = Currencies.FALLBACK,
) {
    /** The hop currency to show, or null when the feature is off. */
    val hop: String? get() = hopCurrency.takeIf { hopEnabled }
}

/** Maps raw SharedPreferences values to [Settings]; invalid entries are ignored. */
object SettingsCodec {
    const val KEY_HOME = "home_currency"
    const val KEY_HOP_ENABLED = "hop_enabled"
    const val KEY_HOP = "hop_currency"
    private const val PREFIX_RATE = "manual_rate_"
    private const val PREFIX_HOME = "manual_home_"

    fun rateKey(code: String): String = PREFIX_RATE + code
    fun homeKey(code: String): String = PREFIX_HOME + code

    fun decode(values: Map<String, *>, defaultHome: String): Settings {
        val home = (values[KEY_HOME] as? String)?.takeIf { Currencies.get(it) != null } ?: defaultHome
        val manual = values.keys
            .filter { it.startsWith(PREFIX_RATE) }
            .mapNotNull { key ->
                val code = key.removePrefix(PREFIX_RATE)
                val rate = (values[key] as? String)?.let(Money::parseRate)
                val rateHome = (values[homeKey(code)] as? String)?.takeIf { Currencies.get(it) != null }
                if (Currencies.get(code) != null && rate != null && rateHome != null) code to ManualRate(rate, rateHome) else null
            }
            .toMap()
        val hopEnabled = values[KEY_HOP_ENABLED] as? Boolean ?: false
        val hop = (values[KEY_HOP] as? String)?.takeIf { Currencies.get(it) != null } ?: Currencies.FALLBACK
        return Settings(home, manual, hopEnabled, hop)
    }
}
