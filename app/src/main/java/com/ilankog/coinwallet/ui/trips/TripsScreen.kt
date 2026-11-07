package com.ilankog.coinwallet.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripSummary
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.components.CodeBadge
import com.ilankog.coinwallet.ui.components.CoinStackArt
import com.ilankog.coinwallet.ui.components.RateChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    state: TripsUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onNewTrip: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coin Wallet") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTrip,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New trip") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.rateStatus is RateStatus.Updating,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { TotalHeader(state) }
                if (state.loaded && state.totals.trips.isEmpty()) {
                    item(key = "empty") { EmptyTrips() }
                }
                items(state.totals.trips, key = { it.trip.id }) { s ->
                    TripCard(s, state.home, onClick = { onOpenTrip(s.trip.id) })
                }
            }
        }
    }
}

internal fun missingHint(n: Int): String =
    if (n == 1) "1 trip without rate not included" else "$n trips without rate not included"

@Composable
private fun TotalHeader(state: TripsUiState) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("All trips in ${state.home.code}", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(state.totals.grandHomeMinor, state.home),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.totals.missingRateCount > 0) {
                Text(missingHint(state.totals.missingRateCount), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            RateChip(state.rateStatus, state.now)
        }
    }
}

@Composable
private fun TripCard(s: TripSummary, home: CurrencyInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CodeBadge(s.info.code)
            Column(Modifier.weight(1f)) {
                Text(s.trip.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Money.format(s.totalMinor, s.info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val homeMinor = s.homeMinor
                if (homeMinor != null) {
                    Text(Money.format(homeMinor, home), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                } else {
                    Text("No rate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                }
                if (s.rate?.source == RateSource.MANUAL) {
                    Text("manual rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun EmptyTrips() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoinStackArt(Modifier.size(120.dp))
        Text("No trips yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tap New trip to start counting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
