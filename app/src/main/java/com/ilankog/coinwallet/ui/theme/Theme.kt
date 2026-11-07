package com.ilankog.coinwallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFFA85A28), // 5.0:1 with white (AA)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DCC8),
    onPrimaryContainer = Color(0xFF3D1E0A),
    secondary = Color(0xFFB8902F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E3B8),
    onSecondaryContainer = Color(0xFF3A2C05),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF2B1D12),
    surface = Color(0xFFFBF6EE),
    onSurface = Color(0xFF2B1D12),
    surfaceVariant = Color(0xFFEFE3D3),
    onSurfaceVariant = Color(0xFF5C4A3A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1E7),
    surfaceContainer = Color(0xFFF5EDE1),
    surfaceContainerHigh = Color(0xFFF0E6D8),
    surfaceContainerHighest = Color(0xFFEADFCF),
    outline = Color(0xFF9C8672),
    outlineVariant = Color(0xFFD9C8B4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE89A63),
    onPrimary = Color(0xFF3D1E0A),
    primaryContainer = Color(0xFF6B3516),
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = Color(0xFFD9B45A),
    onSecondary = Color(0xFF3A2C05),
    secondaryContainer = Color(0xFF54420F),
    onSecondaryContainer = Color(0xFFF6E3B0),
    background = Color(0xFF1A1410),
    onBackground = Color(0xFFF3E9DC),
    surface = Color(0xFF1A1410),
    onSurface = Color(0xFFF3E9DC),
    surfaceVariant = Color(0xFF3A2F26),
    onSurfaceVariant = Color(0xFFD6C4B0),
    surfaceContainerLowest = Color(0xFF140F0C),
    surfaceContainerLow = Color(0xFF201913),
    surfaceContainer = Color(0xFF241C16),
    surfaceContainerHigh = Color(0xFF2E251E),
    surfaceContainerHighest = Color(0xFF392F27),
    outline = Color(0xFFA08C78),
    outlineVariant = Color(0xFF4F4136),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

private val BaseType = Typography()

private val CoinTypography = BaseType.copy(
    displaySmall = BaseType.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseType.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

private val CoinShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun CoinWalletTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CoinTypography,
        shapes = CoinShapes,
        content = content,
    )
}
