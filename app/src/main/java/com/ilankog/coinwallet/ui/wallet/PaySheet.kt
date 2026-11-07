package com.ilankog.coinwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.Conversion
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.PaySuggestion
import com.ilankog.coinwallet.core.Payment
import com.ilankog.coinwallet.ui.components.DenomIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

sealed interface PayPreview {
    data object Empty : PayPreview
    data object Invalid : PayPreview
    data class Insufficient(val totalMinor: Long) : PayPreview
    data class Ok(val s: PaySuggestion, val homeMinor: Long?, val hopMinor: Long?) : PayPreview
}

const val STALE_PAYMENT = "Wallet changed. Check the suggestion again."

fun payPreview(ui: WalletUiState, text: String): PayPreview {
    if (text.isBlank()) return PayPreview.Empty
    val info = ui.summary.info
    val price = Money.parseAmount(text.trim(), info) ?: return PayPreview.Invalid
    val s = Payment.suggest(ui.summary.trip, info, price) ?: return PayPreview.Insufficient(ui.summary.totalMinor)
    val home = if (info.code == ui.home.code) null else ui.summary.rate?.let { Conversion.convert(price, info, ui.home, it.rate) }
    val hop = ui.hop?.let { h -> h.rate?.let { Conversion.convert(price, info, h.info, it) } }
    return PayPreview.Ok(s, home, hop)
}

fun noteTime(at: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(at))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaySheet(ui: WalletUiState, text: String, stale: Boolean, paying: Boolean, onTextChange: (String) -> Unit, onPaid: (PaySuggestion) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PaySheetContent(ui, text, payPreview(ui, text), stale, onTextChange, onPaid, onDismiss, paying)
    }
}

@Composable
fun PaySheetContent(
    ui: WalletUiState,
    text: String,
    preview: PayPreview,
    stale: Boolean,
    onTextChange: (String) -> Unit,
    onPaid: (PaySuggestion) -> Unit,
    onCancel: () -> Unit,
    paying: Boolean = false,
) {
    val info = ui.summary.info
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pay", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Price in ${info.code}") },
            isError = preview is PayPreview.Invalid || preview is PayPreview.Insufficient,
            supportingText = {
                when (preview) {
                    PayPreview.Invalid -> Text("Enter a price above 0")
                    is PayPreview.Insufficient -> Text("You only have ${Money.format(preview.totalMinor, info)}")
                    else -> Text("Smallest coins first")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (stale) Text(STALE_PAYMENT, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (preview is PayPreview.Ok) Suggestion(ui, preview)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { (preview as? PayPreview.Ok)?.let { onPaid(it.s) } }, enabled = preview is PayPreview.Ok && !paying) { Text("Paid") }
        }
    }
}

@Composable
private fun Suggestion(ui: WalletUiState, p: PayPreview.Ok) {
    val info = ui.summary.info
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Hand over", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        p.s.pieces.forEach { (d, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) { DenomIcon(d, info) }
                Spacer(Modifier.width(12.dp))
                Text("$n × ${Money.label(d.value, info)}", style = MaterialTheme.typography.titleMedium)
            }
        }
        HorizontalDivider()
        Text("You hand over ${Money.format(p.s.paidMinor, info)}", style = MaterialTheme.typography.titleMedium)
        if (p.s.changeMinor > 0) {
            Text(
                "Change ${Money.format(p.s.changeMinor, info)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        val equivalents = listOfNotNull(
            p.homeMinor?.let { "≈ " + Money.format(it, ui.home) },
            ui.hop?.let { h -> p.hopMinor?.let { "≈ " + Money.format(it, h.info) + " (hop)" } ?: "Hop: no rate" },
        )
        if (equivalents.isNotEmpty()) {
            Text(
                "Price " + equivalents.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
