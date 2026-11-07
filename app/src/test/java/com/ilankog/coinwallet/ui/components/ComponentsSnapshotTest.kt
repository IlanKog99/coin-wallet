package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.ui.DialogFrame
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class ComponentsSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private val now = 1_762_430_400_000L

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun denominationIcons() = paparazzi.snapBoth {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (code in listOf("EUR", "CZK", "JPY", "IDR")) {
                val info = Currencies.orFallback(code)
                Text(code, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.denominations.forEach { DenomIcon(it, info) }
                }
            }
        }
    }

    @Test
    fun artAndRateChips() = paparazzi.snapBoth {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoinStackArt(Modifier.size(120.dp))
            RateChip(RateStatus.Fresh(now - 2 * 3_600_000L), now)
            RateChip(RateStatus.Offline(now - 3 * 86_400_000L), now)
            RateChip(RateStatus.Updating, now)
            RateChip(RateStatus.None, now)
        }
    }

    @Test
    fun confirmDialog() = paparazzi.snapBoth {
        DialogFrame {
            DialogCard(title = "Delete Prague weekend?", confirmLabel = "Delete", onConfirm = {}, onDismiss = {}, destructive = true) {
                Text("You can undo right after.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    @Test
    fun currencyPicker() = paparazzi.snapBoth {
        DialogFrame { CurrencyPickerDialogContent("Home currency", "", {}, "ILS", {}, {}) }
    }

    @Test
    fun currencyPickerNoMatch() = paparazzi.snapBoth {
        DialogFrame { CurrencyPickerDialogContent("Home currency", "zzz", {}, "ILS", {}, {}) }
    }
}
