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
    // Which picker is open: "home", "hop" or null. A string so it survives rotation.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    SettingsScreen(
        state,
        onBack = onBack,
        onPickHome = { picking = "home" },
        onRefresh = vm::refresh,
        onHopEnabled = vm::setHopEnabled,
        onPickHop = { picking = "hop" },
    )
    val target = picking ?: return
    val hop = target == "hop"
    val close = { picking = null; query = "" }
    AppDialog(onDismiss = close) {
        CurrencyPickerDialogContent(
            title = if (hop) "Hop currency" else "Home currency",
            query = query,
            onQueryChange = { query = it },
            selected = if (hop) state.hop.code else state.home.code,
            onPick = { if (hop) vm.setHop(it.code) else vm.setHome(it.code); close() },
            onDismiss = close,
        )
    }
}
