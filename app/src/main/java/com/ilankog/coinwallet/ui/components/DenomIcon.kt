package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.DenomTier
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.ui.theme.Brass
import com.ilankog.coinwallet.ui.theme.Copper
import com.ilankog.coinwallet.ui.theme.NoteInk
import com.ilankog.coinwallet.ui.theme.NotePaper
import com.ilankog.coinwallet.ui.theme.Silver

/** Original coin (round, metal tier) or note (rectangle, paper tier) art with the face value. */
@Composable
fun DenomIcon(denom: Denomination, info: CurrencyInfo, modifier: Modifier = Modifier) {
    val tier = DenomTier.of(denom, info)
    // Face text is decorative (the row label says the value) and must fit the fixed art, so ignore font scale.
    val fontScale = LocalDensity.current.fontScale
    val text = DenomTier.faceText(denom, info)
    if (denom.kind == DenomKind.COIN) {
        val metal = listOf(Copper, Brass, Silver)[tier]
        Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(metal.shine, metal.face),
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = size.minDimension * 0.75f,
                    ),
                )
                drawCircle(metal.rim, radius = size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                drawCircle(metal.rim.copy(alpha = 0.45f), radius = size.minDimension / 2 - 6.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
            }
            Text(text, color = metal.ink, fontWeight = FontWeight.Bold, fontSize = (faceSize(text, 15) / fontScale).sp, maxLines = 1)
        }
    } else {
        val paper = NotePaper[tier]
        Box(modifier.size(width = 56.dp, height = 34.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(paper, cornerRadius = CornerRadius(6.dp.toPx()))
                val inset = 3.dp.toPx()
                drawRoundRect(
                    Color.Black.copy(alpha = 0.18f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(Color.White.copy(alpha = 0.35f), radius = size.height * 0.3f, center = Offset(size.width * 0.8f, size.height / 2))
            }
            Text(text, color = NoteInk, fontWeight = FontWeight.Bold, fontSize = (faceSize(text, 14) / fontScale).sp, maxLines = 1)
        }
    }
}

private fun faceSize(text: String, base: Int): Int = when {
    text.length <= 2 -> base
    text.length == 3 -> base - 2
    text.length == 4 -> base - 4
    else -> base - 5
}
