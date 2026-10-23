package com.packfare.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packfare.app.domain.Category
import com.packfare.app.domain.Money

/**
 * Spend-by-category ring with a legend on the right.
 * Small gaps between segments keep neighbouring colours readable.
 */
@Composable
fun CategoryDonut(slices: List<Pair<Category, Long>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.second }
    if (total <= 0) return
    val track = MaterialTheme.colorScheme.surfaceVariant

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(112.dp)) {
                val strokeWidth = 16.dp.toPx()
                val inset = strokeWidth / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
                val gap = if (slices.size > 1) 3f else 0f
                var start = -90f
                slices.forEach { (cat, amount) ->
                    val sweep = 360f * amount / total
                    if (sweep - gap > 0.5f) {
                        drawArc(
                            color = cat.color,
                            startAngle = start + gap / 2,
                            sweepAngle = sweep - gap,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                        )
                    }
                    start += sweep
                }
            }
            Text(
                "${slices.size}\n${if (slices.size == 1) "category" else "categories"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { (cat, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(cat.color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${cat.emoji} ${cat.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Money.format(amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
