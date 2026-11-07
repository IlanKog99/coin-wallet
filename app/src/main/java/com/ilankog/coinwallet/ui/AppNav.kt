package com.ilankog.coinwallet.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ilankog.coinwallet.BuildConfig
import com.ilankog.coinwallet.data.AppContainer
import com.ilankog.coinwallet.data.RepoNotice
import com.ilankog.coinwallet.ui.common.text
import com.ilankog.coinwallet.ui.settings.SettingsRoute
import com.ilankog.coinwallet.ui.settings.SettingsViewModel
import com.ilankog.coinwallet.ui.trips.TripsRoute
import com.ilankog.coinwallet.ui.trips.TripsViewModel
import com.ilankog.coinwallet.ui.wallet.WalletRoute
import com.ilankog.coinwallet.ui.wallet.WalletViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.Serializable

@Serializable
data object TripsDest

@Serializable
data class WalletDest(val tripId: String)

@Serializable
data object SettingsDest

@Composable
fun CoinWalletNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // App-wide repository notices (corrupt reset, save failure, delete + Undo) survive navigation.
    LaunchedEffect(container) {
        container.trips.notice.filterNotNull().collect { n ->
            val deleted = n as? RepoNotice.Deleted
            val result = snackbar.showSnackbar(
                message = n.text(),
                actionLabel = if (deleted != null) "Undo" else null,
                withDismissAction = deleted == null,
                duration = if (deleted != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            // Cleared only after it was shown: if the activity is recreated mid-snackbar, it shows again (Undo kept).
            container.trips.clearNotice(n)
            if (result == SnackbarResult.ActionPerformed && deleted != null) container.trips.restore(deleted.trip)
        }
    }

    NavHost(navController = nav, startDestination = TripsDest) {
        composable<TripsDest> {
            val vm = viewModel { TripsViewModel(container.trips, container.rates, container.settings) }
            TripsRoute(
                vm = vm,
                snackbarHostState = snackbar,
                onOpenTrip = { nav.navigate(WalletDest(it)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(SettingsDest) { launchSingleTop = true } },
            )
        }
        composable<WalletDest> { entry ->
            val tripId = entry.toRoute<WalletDest>().tripId
            val vm = viewModel { WalletViewModel(tripId, container.trips, container.rates, container.settings) }
            WalletRoute(vm = vm, snackbarHostState = snackbar, onBack = { popIfTop(nav, entry) })
        }
        composable<SettingsDest> { entry ->
            val vm = viewModel { SettingsViewModel(container.rates, container.settings, BuildConfig.VERSION_NAME) }
            SettingsRoute(vm = vm, onBack = { popIfTop(nav, entry) })
        }
    }
}

/**
 * Pops only while [entry] is the top of the back stack, so a double back tap can't pop the trips list.
 * Unlike a RESUMED check, this also works during the enter transition (e.g. a trip that is already gone).
 */
private fun popIfTop(nav: NavHostController, entry: NavBackStackEntry) {
    if (nav.currentBackStackEntry?.id == entry.id) nav.popBackStack()
}
