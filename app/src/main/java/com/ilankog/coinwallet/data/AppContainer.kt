package com.ilankog.coinwallet.data

import android.content.Context
import com.ilankog.coinwallet.core.Currencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale

/** Manual DI: one instance per process, owned by CoinWalletApp. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val trips = TripRepository(context.filesDir)
    val rates = RateRepository(context.filesDir, UrlConnectionFetcher(), appScope)
    val settings: SettingsSource = SettingsStore(
        context.getSharedPreferences("settings", Context.MODE_PRIVATE),
        Currencies.defaultHome(Locale.getDefault()),
    )
}
