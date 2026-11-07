package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.ui.theme.Brass
import com.ilankog.coinwallet.ui.theme.Copper
import com.ilankog.coinwallet.ui.theme.Silver

/** Empty-state illustration: three stacked coins plus one loose coin. Original art. */
@Composable
fun CoinStackArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val coinW = w * 0.62f
        val coinH = h * 0.2f
        val edge = h * 0.09f
        val left = (w - coinW) / 2
        listOf(Copper to 0.66f, Brass to 0.47f, Silver to 0.28f).forEach { (m, top) ->
            val y = h * top
            drawRect(m.rim, topLeft = Offset(left, y + coinH / 2), size = Size(coinW, edge))
            drawOval(m.rim, topLeft = Offset(left, y + edge), size = Size(coinW, coinH))
            drawOval(
                Brush.linearGradient(listOf(m.shine, m.face), start = Offset(left, y), end = Offset(left + coinW, y + coinH)),
                topLeft = Offset(left, y),
                size = Size(coinW, coinH),
            )
            drawOval(
                m.rim.copy(alpha = 0.5f),
                topLeft = Offset(left + coinW * 0.12f, y + coinH * 0.18f),
                size = Size(coinW * 0.76f, coinH * 0.64f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        val c = Offset(w * 0.84f, h * 0.2f)
        drawCircle(Brush.radialGradient(listOf(Brass.shine, Brass.face), center = c, radius = w * 0.13f), radius = w * 0.11f, center = c)
        drawCircle(Brass.rim, radius = w * 0.11f, center = c, style = Stroke(width = 3.dp.toPx()))
    }
}
