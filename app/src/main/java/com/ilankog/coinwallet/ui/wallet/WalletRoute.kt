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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilankog.coinwallet.core.CurrencyInfo
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
    data object Pay : WalletDialog
}

// Saved as a plain string so an open dialog survives rotation and process death.
private fun WalletDialog.save(): String = when (this) {
    is WalletDialog.Count -> "count:${d.key}"
    WalletDialog.Rate -> "rate"
    WalletDialog.Reset -> "reset"
    WalletDialog.Delete -> "delete"
    WalletDialog.Edit -> "edit"
    WalletDialog.Pay -> "pay"
}

private fun restoreDialog(saved: String?, info: CurrencyInfo): WalletDialog? = when {
    saved == null -> null
    saved.startsWith("count:") -> info.denominations.firstOrNull { it.key == saved.removePrefix("count:") }?.let(WalletDialog::Count)
    saved == "rate" -> WalletDialog.Rate
    saved == "reset" -> WalletDialog.Reset
    saved == "delete" -> WalletDialog.Delete
    saved == "edit" -> WalletDialog.Edit
    saved == "pay" -> WalletDialog.Pay
    else -> null
}

@Composable
fun WalletRoute(vm: WalletViewModel, snackbarHostState: SnackbarHostState, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var savedDialog by rememberSaveable { mutableStateOf<String?>(null) }
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
                    onCountClick = { savedDialog = WalletDialog.Count(it).save() },
                    onEdit = { savedDialog = WalletDialog.Edit.save() },
                    onSetRate = { savedDialog = WalletDialog.Rate.save() },
                    onClearRate = vm::clearManualRate,
                    onReset = { savedDialog = WalletDialog.Reset.save() },
                    onDelete = { savedDialog = WalletDialog.Delete.save() },
                    onPay = { savedDialog = WalletDialog.Pay.save() },
                    onDismissNote = vm::dismissNote,
                ),
            )
            val close = { savedDialog = null }
            when (val d = restoreDialog(savedDialog, ui.summary.info)) {
                null -> Unit
                is WalletDialog.Count -> {
                    var text by rememberSaveable(d.d.key) { mutableStateOf(TripRules.count(ui.summary.trip, d.d).toString()) }
                    var error by rememberSaveable(d.d.key) { mutableStateOf<String?>(null) }
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
                    var text by rememberSaveable { mutableStateOf(current) }
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
                    currencyLocked = !TripRules.canChangeCurrency(ui.summary.trip),
                    onSave = { name, code -> vm.editTrip(name, code); close() },
                    onDismiss = close,
                )
                WalletDialog.Pay -> {
                    var text by rememberSaveable { mutableStateOf("") }
                    var stale by rememberSaveable { mutableStateOf(false) }
                    val paying by vm.paying.collectAsStateWithLifecycle()
                    PaySheet(
                        ui = ui,
                        text = text,
                        stale = stale,
                        paying = paying,
                        onTextChange = { text = it; stale = false },
                        onPaid = { s -> vm.pay(s) { ok -> if (ok) close() else stale = true } },
                        onDismiss = close,
                    )
                }
            }
        }
    }
}
