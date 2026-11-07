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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.ChangeNote
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.core.TripSummary
import com.ilankog.coinwallet.ui.components.DenomIcon
import java.time.ZoneId

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
    val onPay: () -> Unit = {},
    val onDismissNote: (String) -> Unit = {},
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
fun WalletScreen(
    state: WalletUiState,
    snackbarHostState: SnackbarHostState,
    actions: WalletActions,
    zone: ZoneId = ZoneId.systemDefault(),
) {
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
            items(s.trip.changeDue.asReversed(), key = { "note:" + it.id }) { n ->
                ChangeNoteCard(n, s.info, zone, actions.onDismissNote)
            }
            item(key = "header") { WalletHeader(state, actions.onPay) }
            item(key = "coins") { SectionTitle("Coins") }
            items(coins, key = { it.key }) { d -> DenomRow(d, s, actions) }
            item(key = "notes") { SectionTitle("Notes") }
            items(notes, key = { it.key }) { d -> DenomRow(d, s, actions) }
        }
    }
}

@Composable
private fun WalletHeader(state: WalletUiState, onPay: () -> Unit) {
    val s = state.summary
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("In your pocket", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Button(onClick = onPay, enabled = s.totalMinor > 0) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pay")
                }
            }
            Text(named(Money.format(s.totalMinor, s.info), s.info.name), style = MaterialTheme.typography.displaySmall)
            if (s.info.code != state.home.code) {
                val homeMinor = s.homeMinor
                Text(
                    if (homeMinor != null) named("≈ " + Money.format(homeMinor, state.home), state.home.name) else AnnotatedString("No rate"),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            state.hop?.let { h ->
                val minor = h.minor
                Text(
                    if (minor != null) named("≈ " + Money.format(minor, h.info), h.info.name + " (hop)") else AnnotatedString("Hop: no rate"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            rateLine(state)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** "12,500 Ft · Hungarian Forint" with the name in smaller text; long names wrap instead of being cut. */
@Composable
private fun named(amount: String, name: String): AnnotatedString = buildAnnotatedString {
    append(amount)
    withStyle(MaterialTheme.typography.bodyMedium.toSpanStyle()) { append(" · $name") }
}

@Composable
private fun ChangeNoteCard(n: ChangeNote, info: CurrencyInfo, zone: ZoneId, onDone: (String) -> Unit) {
    val amount = Money.format(n.amountMinor, info)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Change to add: $amount", style = MaterialTheme.typography.titleMedium)
                Text(noteTime(n.at, zone), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = { onDone(n.id) },
                modifier = Modifier.semantics { contentDescription = "Done, change of $amount added" },
            ) { Text("Done") }
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
            TextButton(
                onClick = { actions.onCountClick(d) },
                modifier = Modifier.widthIn(min = 64.dp).semantics { contentDescription = "$count × $label, tap to set count" },
            ) {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            }
            FilledTonalIconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onIncrement(d) },
                enabled = count < TripRules.MAX_COUNT,
            ) { Icon(Icons.Default.Add, contentDescription = "Add one $label") }
        }
    }
}
