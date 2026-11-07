package com.ilankog.coinwallet.ui.wallet

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.ui.DialogFrame
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class WalletScreenSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(trip: Trip) = paparazzi.snapBoth {
        WalletScreen(SampleData.walletState(trip), remember { SnackbarHostState() }, SampleData.noActions)
    }

    @Test
    fun ecbRate() = snap(SampleData.prague)

    @Test
    fun zeroDecimalYen() = snap(SampleData.tokyo)

    @Test
    fun manualRate() = snap(SampleData.newYork)

    @Test
    fun noRate() = snap(SampleData.bangkok)

    @Test
    fun countDialogError() = paparazzi.snapBoth {
        DialogFrame { CountDialogContent("10 Kč", "12345", COUNT_ERROR, {}, {}, {}) }
    }

    @Test
    fun rateDialog() = paparazzi.snapBoth {
        DialogFrame { RateDialogContent("USD", "ILS", "3.6", {}, {}, {}) }
    }

    @Test
    fun rateDialogInvalid() = paparazzi.snapBoth {
        DialogFrame { RateDialogContent("USD", "ILS", "0", {}, {}, {}) }
    }

    @Test
    fun deleteConfirm() = paparazzi.snapBoth {
        DialogFrame { ConfirmDialogContent("Delete Prague weekend?", "You can undo right after.", "Delete", true, {}, {}) }
    }
}
