package com.ilankog.coinwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.ui.components.DialogCard

const val COUNT_ERROR = "Enter a whole number from 0 to 9999"
const val RATE_ERROR = "Enter a rate above 0, up to 1,000,000"

@Composable
fun CountDialogContent(
    label: String,
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard(title = "How many $label?", confirmLabel = "OK", onConfirm = onConfirm, onDismiss = onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            isError = error != null,
            supportingText = { Text(error ?: "0 to ${TripRules.MAX_COUNT}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun RateDialogContent(
    from: String,
    home: String,
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val valid = Money.parseRate(text) != null
    val showError = text.isNotBlank() && !valid
    DialogCard(title = "Manual rate", confirmLabel = "Save", onConfirm = onConfirm, onDismiss = onDismiss, confirmEnabled = valid) {
        Text(
            "Use the rate you actually got, for example at an exchange booth.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1 $from =", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                isError = showError,
                singleLine = true,
                suffix = { Text(home) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        if (showError) Text(RATE_ERROR, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun ConfirmDialogContent(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard(title = title, confirmLabel = confirmLabel, onConfirm = onConfirm, onDismiss = onDismiss, destructive = destructive) {
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
