package com.ilankog.coinwallet.ui.theme

import androidx.compose.ui.graphics.Color

/** Original coin metal palette used by the denomination art. */
data class Metal(val face: Color, val shine: Color, val rim: Color, val ink: Color)

val Copper = Metal(Color(0xFFC27A4A), Color(0xFFE9A97C), Color(0xFF8A4A22), Color(0xFF3D1E0A))
val Brass = Metal(Color(0xFFD9B45A), Color(0xFFF3DC95), Color(0xFF9C7A26), Color(0xFF3A2C05))
val Silver = Metal(Color(0xFFC9CCD1), Color(0xFFF1F2F4), Color(0xFF8D9198), Color(0xFF2A2D31))
val NotePaper = listOf(Color(0xFF9FC3A5), Color(0xFFE3B98A), Color(0xFFA9B8D8))
val NoteInk = Color(0xFF2B1D12)
