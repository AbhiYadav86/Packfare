package com.packfare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.packfare.app.domain.Money
import com.packfare.app.ui.theme.BalanceColors

/** "₹4,200 of ₹10,000" with a progress bar that turns red when over budget. */
@Composable
fun BudgetBar(spentPaise: Long, budgetPaise: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Spent ${Money.format(spentPaise)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (budgetPaise > 0) {
                val left = budgetPaise - spentPaise
                Text(
                    text = if (left >= 0) "${Money.format(left)} left" else "${Money.format(-left)} over",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else BalanceColors.negative,
                )
            }
        }
        if (budgetPaise > 0) {
            val fraction = (spentPaise.toFloat() / budgetPaise).coerceIn(0f, 1f)
            val over = spentPaise > budgetPaise
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (over) BalanceColors.negative else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "Budget ${Money.format(budgetPaise)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
