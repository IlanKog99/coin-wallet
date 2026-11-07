package com.ilankog.coinwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.ilankog.coinwallet.ui.theme.CoinWalletTheme

// Paparazzi 1.3.5 (layoutlib 14.0.11) lacks StaticLayout.Builder.setUseBoundsForWidth, which Compose
// ui-text 1.8 calls when SDK_INT >= 35. Render with API 34 for snapshots only; the app stays compileSdk 35.
fun coinPaparazzi() = Paparazzi(
    environment = detectEnvironment().copy(compileSdkVersion = 34),
    deviceConfig = DeviceConfig.PIXEL_5,
    theme = "android:Theme.Material.Light.NoActionBar",
    maxPercentDifference = 0.0,
)

/** Snapshots [content] in light and dark theme ("light" / "dark" suffix). */
fun Paparazzi.snapBoth(content: @Composable () -> Unit) {
    for (dark in listOf(false, true)) {
        snapshot(if (dark) "dark" else "light") {
            CoinWalletTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

/** Paparazzi cannot render Dialog windows; this mimics the scrim around a DialogCard. */
@Composable
fun DialogFrame(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Mimics a ModalBottomSheet around sheet content. */
@Composable
fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(top = 24.dp)) { content() }
        }
    }
}
