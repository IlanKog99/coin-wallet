package com.ilankog.coinwallet.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.ui.components.CodeBadge
import com.ilankog.coinwallet.ui.components.CurrencyPickerContent

fun tripNameError(name: String): String? {
    val t = name.trim()
    return when {
        t.isEmpty() -> "Name is required"
        t.length > TripRules.MAX_NAME -> "Max ${TripRules.MAX_NAME} characters"
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSheet(
    title: String,
    initialName: String,
    initialCurrency: String?,
    currencyLocked: Boolean,
    onSave: (name: String, currency: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Inside the sheet: the sheet has its own window and back dispatcher.
        BackHandler(enabled = picking) { picking = false }
        TripSheetContent(
            title = title,
            name = name,
            nameError = if (touched) tripNameError(name) else null,
            currency = currency?.let(Currencies::get),
            currencyLocked = currencyLocked,
            picking = picking,
            query = query,
            onNameChange = { name = it; touched = true },
            onPickCurrency = { picking = true },
            onQueryChange = { query = it },
            onCurrencyPicked = { currency = it.code; picking = false; query = "" },
            onClosePicker = { picking = false },
            onSave = {
                val clean = TripRules.cleanName(name)
                val code = currency
                if (clean != null && code != null) onSave(clean, code) else touched = true
            },
            onCancel = onDismiss,
        )
    }
}

@Composable
fun TripSheetContent(
    title: String,
    name: String,
    nameError: String?,
    currency: CurrencyInfo?,
    currencyLocked: Boolean,
    picking: Boolean,
    query: String,
    onNameChange: (String) -> Unit,
    onPickCurrency: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCurrencyPicked: (CurrencyInfo) -> Unit,
    onClosePicker: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
        if (picking) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClosePicker) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Choose currency", style = MaterialTheme.typography.titleLarge)
            }
            CurrencyPickerContent(query, onQueryChange, currency?.code, onCurrencyPicked, Modifier.heightIn(max = 520.dp))
        } else {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Trip name") },
                    isError = nameError != null,
                    supportingText = { Text(nameError ?: "${name.trim().length}/${TripRules.MAX_NAME}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CurrencyField(currency, currencyLocked, onPickCurrency)
                if (currencyLocked) {
                    Text(
                        "Reset counts to change currency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = TripRules.cleanName(name) != null && currency != null) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun CurrencyField(currency: CurrencyInfo?, locked: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (currency != null) {
                CodeBadge(currency.code)
                Column(Modifier.weight(1f)) {
                    Text("Currency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currency.name, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text("Choose currency", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (!locked) Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
}
