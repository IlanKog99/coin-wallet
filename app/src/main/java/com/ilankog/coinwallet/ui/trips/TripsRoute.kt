package com.ilankog.coinwallet.ui.trips

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilankog.coinwallet.ui.common.text

@Composable
fun TripsRoute(
    vm: TripsViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenTrip: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    LifecycleStartEffect(vm) {
        vm.refreshIfStale()
        onStopOrDispose { }
    }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.messageShown()
        snackbarHostState.showSnackbar(m.text())
    }

    TripsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = vm::refresh,
        onNewTrip = { showSheet = true },
        onOpenTrip = onOpenTrip,
        onOpenSettings = onOpenSettings,
    )
    if (showSheet) {
        TripSheet(
            title = "New trip",
            initialName = "",
            initialCurrency = null,
            currencyLocked = false,
            onSave = { name, code -> vm.createTrip(name, code); showSheet = false },
            onDismiss = { showSheet = false },
        )
    }
}
