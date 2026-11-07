package com.ilankog.coinwallet.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilankog.coinwallet.ui.components.AppDialog
import com.ilankog.coinwallet.ui.components.CurrencyPickerDialogContent

@Composable
fun SettingsRoute(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    SettingsScreen(state, onBack = onBack, onPickHome = { picking = true }, onRefresh = vm::refresh)
    if (picking) {
        val close = { picking = false; query = "" }
        AppDialog(onDismiss = close) {
            CurrencyPickerDialogContent(
                title = "Home currency",
                query = query,
                onQueryChange = { query = it },
                selected = state.home.code,
                onPick = { vm.setHome(it.code); close() },
                onDismiss = close,
            )
        }
    }
}
