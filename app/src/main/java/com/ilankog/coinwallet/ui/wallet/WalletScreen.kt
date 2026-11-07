package com.ilankog.coinwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.core.TripSummary
import com.ilankog.coinwallet.ui.components.DenomIcon

data class WalletActions(
    val onBack: () -> Unit,
    val onIncrement: (Denomination) -> Unit,
    val onDecrement: (Denomination) -> Unit,
    val onCountClick: (Denomination) -> Unit,
    val onEdit: () -> Unit,
    val onSetRate: () -> Unit,
    val onClearRate: () -> Unit,
    val onReset: () -> Unit,
    val onDelete: () -> Unit,
)

fun rateLine(state: WalletUiState): String? {
    val info = state.summary.info
    if (info.code == state.home.code) return null
    val r = state.summary.rate ?: return "Set a manual rate from the menu, or refresh rates."
    val base = "1 ${info.code} = ${Money.formatRate(r.rate)} ${state.home.code}"
    return when (r.source) {
        RateSource.MANUAL -> "$base · manual"
        RateSource.ECB -> listOfNotNull(base, state.rateDate?.let { "ECB $it" }).joinToString(" · ")
        RateSource.SAME -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(state: WalletUiState, snackbarHostState: SnackbarHostState, actions: WalletActions) {
    val s = state.summary
    var menuOpen by remember { mutableStateOf(false) }
    val sameCurrency = s.info.code == state.home.code
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.trip.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Edit trip") }, onClick = { menuOpen = false; actions.onEdit() })
                            if (!sameCurrency) {
                                DropdownMenuItem(text = { Text("Set manual rate") }, onClick = { menuOpen = false; actions.onSetRate() })
                            }
                            if (state.hasManualRate) {
                                DropdownMenuItem(text = { Text("Clear manual rate") }, onClick = { menuOpen = false; actions.onClearRate() })
                            }
                            DropdownMenuItem(text = { Text("Reset counts") }, onClick = { menuOpen = false; actions.onReset() })
                            DropdownMenuItem(
                                text = { Text("Delete trip", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; actions.onDelete() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val coins = s.info.denominations.filter { it.kind == DenomKind.COIN }
        val notes = s.info.denominations.filter { it.kind == DenomKind.NOTE }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { WalletHeader(state) }
            item(key = "coins") { SectionTitle("Coins") }
            items(coins, key = { it.key }) { d -> DenomRow(d, s, actions) }
            item(key = "notes") { SectionTitle("Notes") }
            items(notes, key = { it.key }) { d -> DenomRow(d, s, actions) }
        }
    }
}

@Composable
private fun WalletHeader(state: WalletUiState) {
    val s = state.summary
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("In your pocket", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(s.totalMinor, s.info),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (s.info.code != state.home.code) {
                val homeMinor = s.homeMinor
                Text(
                    if (homeMinor != null) "≈ " + Money.format(homeMinor, state.home) else "No rate",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            rateLine(state)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun DenomRow(d: Denomination, s: TripSummary, actions: WalletActions) {
    val count = TripRules.count(s.trip, d)
    val label = Money.label(d.value, s.info)
    val haptics = LocalHapticFeedback.current
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) { DenomIcon(d, s.info) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    if (count == 0) " " else "= " + Money.format(d.value * count, s.info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onDecrement(d) },
                enabled = count > 0,
            ) { Icon(Icons.Default.Remove, contentDescription = "Remove one $label") }
            TextButton(onClick = { actions.onCountClick(d) }, modifier = Modifier.widthIn(min = 64.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            }
            FilledTonalIconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onIncrement(d) },
                enabled = count < TripRules.MAX_COUNT,
            ) { Icon(Icons.Default.Add, contentDescription = "Add one $label") }
        }
    }
}
