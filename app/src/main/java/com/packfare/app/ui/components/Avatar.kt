package com.packfare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val avatarColors = listOf(
    Color(0xFFC2500F), Color(0xFF006A6A), Color(0xFFB0226A), Color(0xFF5B5BD6),
    Color(0xFF15803D), Color(0xFFB45309), Color(0xFF0369A1), Color(0xFF7C3AED),
)

/** Coloured circle with the person's initial. Colour is stable per person id. */
@Composable
fun Avatar(name: String, id: Long, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = avatarColors[(id % avatarColors.size).toInt().let { if (it < 0) -it else it }]
    Box(modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}
