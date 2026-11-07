package com.ilankog.coinwallet.ui.wallet

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.ilankog.coinwallet.core.ChangeNote
import com.ilankog.coinwallet.core.Conversion
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.ui.DialogFrame
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

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

    private fun withHop(ui: WalletUiState): WalletUiState {
        val eur = Currencies.orFallback("EUR")
        val rate = Conversion.crossRate(SampleData.snapshot, ui.summary.info.code, "EUR")!!
        return ui.copy(hop = HopValue(eur, Conversion.convert(ui.summary.totalMinor, ui.summary.info, eur, rate), rate))
    }

    @Test
    fun notesAndHop() = paparazzi.snapBoth {
        val trip = SampleData.prague.copy(
            changeDue = listOf(ChangeNote("n1", 800, 1_762_500_000_000L), ChangeNote("n2", 1_550, 1_762_520_000_000L)),
        )
        WalletScreen(withHop(SampleData.walletState(trip)), remember { SnackbarHostState() }, SampleData.noActions, ZoneId.of("UTC"))
    }

    private fun paySnap(text: String) = paparazzi.snapBoth {
        val ui = withHop(SampleData.walletState())
        PaySheetContent(ui, text, payPreview(ui, text), stale = false, onTextChange = {}, onPaid = {}, onCancel = {})
    }

    @Test
    fun paySheet() = paySnap("22.50")

    @Test
    fun paySheetInsufficient() = paySnap("600")
}
