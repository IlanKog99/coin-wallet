package com.ilankog.coinwallet

import android.app.Application
import com.ilankog.coinwallet.data.AppContainer

class CoinWalletApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
