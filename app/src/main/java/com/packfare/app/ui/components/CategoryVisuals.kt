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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packfare.app.domain.Category

/** Emoji + chart colour per category. Emoji keep the APK small and look friendly. */
val Category.emoji: String
    get() = when (this) {
        Category.TRAVEL -> "🚆"
        Category.STAY -> "🏨"
        Category.FOOD -> "🍛"
        Category.ACTIVITIES -> "🎡"
        Category.SHOPPING -> "🛍️"
        Category.OTHER -> "📦"
    }

val Category.color: Color
    get() = when (this) {
        Category.TRAVEL -> Color(0xFF0E8A8A)
        Category.STAY -> Color(0xFF5B5BD6)
        Category.FOOD -> Color(0xFFE8702A)
        Category.ACTIVITIES -> Color(0xFFD63384)
        Category.SHOPPING -> Color(0xFFD99A00)
        Category.OTHER -> Color(0xFF8A817C)
    }

/** Emoji on a soft tinted circle, used in expense rows. */
@Composable
fun CategoryBadge(category: Category, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(category.color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.emoji, fontSize = (size.value * 0.45f).sp)
    }
}
