package com.ilankog.coinwallet.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.ui.components.AppDialog
import com.ilankog.coinwallet.ui.trips.TripSheet

private sealed interface WalletDialog {
    data class Count(val d: Denomination) : WalletDialog
    data object Rate : WalletDialog
    data object Reset : WalletDialog
    data object Delete : WalletDialog
    data object Edit : WalletDialog
}

@Composable
fun WalletRoute(vm: WalletViewModel, snackbarHostState: SnackbarHostState, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<WalletDialog?>(null) }
    var left by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (!left) {
            left = true
            onBack()
        }
    }

    when (val st = state) {
        WalletState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        WalletState.NotFound -> LaunchedEffect(Unit) { leave() }
        is WalletState.Ready -> {
            val ui = st.ui
            WalletScreen(
                state = ui,
                snackbarHostState = snackbarHostState,
                actions = WalletActions(
                    onBack = leave,
                    onIncrement = vm::increment,
                    onDecrement = vm::decrement,
                    onCountClick = { dialog = WalletDialog.Count(it) },
                    onEdit = { dialog = WalletDialog.Edit },
                    onSetRate = { dialog = WalletDialog.Rate },
                    onClearRate = vm::clearManualRate,
                    onReset = { dialog = WalletDialog.Reset },
                    onDelete = { dialog = WalletDialog.Delete },
                ),
            )
            val close = { dialog = null }
            when (val d = dialog) {
                null -> Unit
                is WalletDialog.Count -> {
                    var text by remember(d) { mutableStateOf(TripRules.count(ui.summary.trip, d.d).toString()) }
                    var error by remember(d) { mutableStateOf<String?>(null) }
                    AppDialog(onDismiss = close) {
                        CountDialogContent(
                            label = Money.label(d.d.value, ui.summary.info),
                            text = text,
                            error = error,
                            onTextChange = { text = it; error = null },
                            onConfirm = {
                                val n = TripRules.parseCount(text)
                                if (n == null) error = COUNT_ERROR else { vm.setCount(d.d, n); close() }
                            },
                            onDismiss = close,
                        )
                    }
                }
                WalletDialog.Rate -> {
                    val current = ui.summary.rate?.takeIf { it.source == RateSource.MANUAL }?.rate?.toPlainString() ?: ""
                    var text by remember { mutableStateOf(current) }
                    AppDialog(onDismiss = close) {
                        RateDialogContent(
                            from = ui.summary.info.code,
                            home = ui.home.code,
                            text = text,
                            onTextChange = { text = it },
                            onConfirm = { Money.parseRate(text)?.let { vm.setManualRate(it); close() } },
                            onDismiss = close,
                        )
                    }
                }
                WalletDialog.Reset -> AppDialog(onDismiss = close) {
                    ConfirmDialogContent(
                        title = "Reset all counts?",
                        body = "All coins and notes in ${ui.summary.trip.name} go back to 0.",
                        confirmLabel = "Reset",
                        destructive = false,
                        onConfirm = { vm.reset(); close() },
                        onDismiss = close,
                    )
                }
                WalletDialog.Delete -> AppDialog(onDismiss = close) {
                    ConfirmDialogContent(
                        title = "Delete ${ui.summary.trip.name}?",
                        body = "You can undo right after.",
                        confirmLabel = "Delete",
                        destructive = true,
                        onConfirm = { close(); vm.delete() },
                        onDismiss = close,
                    )
                }
                WalletDialog.Edit -> TripSheet(
                    title = "Edit trip",
                    initialName = ui.summary.trip.name,
                    initialCurrency = ui.summary.trip.currency,
                    currencyLocked = TripRules.hasCounts(ui.summary.trip),
                    onSave = { name, code -> vm.editTrip(name, code); close() },
                    onDismiss = close,
                )
            }
        }
    }
}
