package com.ilankog.coinwallet.ui.trips

import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun TripsRoute(
    vm: TripsViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenTrip: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val existing = state.totals.trips.map { it.trip.id }
    // Ids of trips that no longer exist (deleted elsewhere) drop out of the selection.
    val selected = selectedIds.filter { it in existing }.toSet()
    BackHandler(enabled = selected.isNotEmpty()) { selectedIds = emptyList() }

    LifecycleStartEffect(vm) {
        vm.refreshIfStale()
        onStopOrDispose { }
    }
    // Keyed on vm, not on the message: clearing the message must not cancel the snackbar being shown.
    LaunchedEffect(vm) {
        vm.message.filterNotNull().collect { m ->
            vm.messageShown()
            snackbarHostState.showSnackbar(m.text())
        }
    }

    TripsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = vm::refresh,
        onNewTrip = { showSheet = true },
        onOpenTrip = onOpenTrip,
        onOpenSettings = onOpenSettings,
        selected = selected,
        onLongPressTrip = { selectedIds = listOf(it) },
        onToggleTrip = { id -> selectedIds = if (id in selected) (selected - id).toList() else (selected + id).toList() },
        onSelectAll = { selectedIds = existing },
        onClearSelection = { selectedIds = emptyList() },
        onDeleteSelected = { vm.deleteTrips(selected); selectedIds = emptyList() },
        onSwipeDelete = { vm.deleteTrips(setOf(it)) },
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
