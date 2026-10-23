@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.packfare.app.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packfare.app.domain.CashAlert
import com.packfare.app.domain.Money
import com.packfare.app.domain.PaceStatus
import com.packfare.app.domain.PaymentMethod
import com.packfare.app.domain.RunwayReport
import com.packfare.app.ui.components.CategoryDonut
import com.packfare.app.ui.theme.BalanceColors
import com.packfare.app.ui.theme.HeroGradient

/** The "Today" tab: daily budget runway, cash wallet, overnight savings, where money went. */
@Composable
internal fun OverviewTab(
    state: TripDetailState,
    onEditPlan: () -> Unit,
    onAddCash: () -> Unit,
    onDeleteCash: (Long) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RunwayCard(state.runway, state.members.size, onEditPlan) }

        if (state.legs.isNotEmpty()) {
            item {
                val heads = state.members.size.coerceAtLeast(1)
                val planned = state.legs.sumOf { it.leg.farePaise } * heads
                val spentTravel = state.spentByCategory.firstOrNull { it.first == com.packfare.app.domain.Category.TRAVEL }?.second ?: 0L
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🚆 Getting around", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Fares planned: ≈ ${Money.format(planned)}" + (if (heads > 1) " for $heads people" else "") +
                                " · spent on travel so far: ${Money.format(spentTravel)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Included in your budget. Log tickets as Travel expenses to track them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        state.cash?.let { cash ->
            item {
                val alertColor = when (cash.alert) {
                    CashAlert.NONE -> CardDefaults.cardColors()
                    else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                }
                Card(Modifier.fillMaxWidth(), colors = alertColor) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("💵 Cash in my pocket", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    Money.format(cash.cashOnHandPaise),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (cash.cashOnHandPaise < 0) BalanceColors.negative else Color.Unspecified,
                                )
                            }
                            FilledTonalButton(onClick = onAddCash) { Text("+ Withdrew cash") }
                        }
                        when (cash.alert) {
                            CashAlert.LOW -> Text("Running low. Find an ATM while you have signal and a town nearby.")
                            CashAlert.CARRY_FOR_REMOTE -> Text(
                                "${cash.remotePlace} has few ATMs. Carry about ${Money.format(cash.suggestedCashPaise)} " +
                                    "in cash for your stay there.",
                            )
                            CashAlert.NONE -> Unit
                        }
                        if (state.cashTopUps.isEmpty()) {
                            Text(
                                "Log ATM withdrawals here and mark cash expenses as 💵, and Packfare keeps count.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.cashTopUps.take(3).forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "ATM ${Money.format(t.amountPaise)}" + (t.note?.let { " · $it" } ?: ""),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = { onDeleteCash(t.id) }) {
                                        Text("Undo", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.overnightSavingsPaise > 0) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🌙 Overnight journeys", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Sleeping on ${state.legs.count { it.leg.overnight }} night bus/train rides saves your group " +
                                "about ${Money.format(state.overnightSavingsPaise)} in stays.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        val permits = state.legs.mapNotNull { row -> row.permitNote?.let { row.leg.toName to it } }.distinctBy { it.first }
        if (permits.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📜 Permits on this route", style = MaterialTheme.typography.titleSmall)
                        permits.forEach { (place, note) -> Text("$place: $note", style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            "Save the permit to your Vault so it opens without signal.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (state.spentByCategory.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Where it went", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))
                        CategoryDonut(state.spentByCategory)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PaymentMethod.entries.forEach { m ->
                                Text(
                                    "${m.emoji} ${m.label} ${Money.format(state.spentByMethod[m] ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunwayCard(r: RunwayReport?, people: Int, onEditPlan: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroGradient).padding(20.dp)) {
        Column {
            if (r == null || r.status == PaceStatus.NO_BUDGET) {
                Text("Set a budget and dates", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Packfare then tells you how much you can spend each day and how long your money lasts.",
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onEditPlan) { Text("Set budget") }
                return@Column
            }

            val headline = when (r.status) {
                PaceStatus.NOT_STARTED -> if (r.daysUntilStart == 1) "Starts tomorrow" else "Starts in ${r.daysUntilStart} days"
                PaceStatus.FINISHED -> "Trip complete 🎉"
                else -> "Day ${r.dayNumber} of ${r.plannedDays}"
            }
            Text(headline, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)

            val big = if (r.status == PaceStatus.NOT_STARTED || r.status == PaceStatus.FINISHED) {
                Money.format(r.dailyTargetPaise)
            } else {
                Money.format((r.allowancePerDayPaise - r.todaySpentPaise).coerceAtLeast(0))
            }
            Text(big, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                when (r.status) {
                    PaceStatus.NOT_STARTED, PaceStatus.FINISHED -> "daily budget" + if (people > 1) " for the group" else ""
                    else -> "left to spend today (spent ${Money.format(r.todaySpentPaise)})"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(14.dp))
            val budget = r.spentPaise + r.remainingPaise
            val fraction = if (budget > 0) (r.spentPaise.toFloat() / budget).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (r.remainingPaise >= 0) Color.White else Color(0xFFFFD166),
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(10.dp))

            Row {
                Stat("Avg / day", Money.format(r.avgPerDayPaise), Modifier.weight(1f))
                Stat("Target / day", Money.format(r.dailyTargetPaise), Modifier.weight(1f))
                Stat(
                    if (r.remainingPaise >= 0) "Left" else "Over",
                    Money.format(kotlin.math.abs(r.remainingPaise)),
                    Modifier.weight(1f),
                )
            }

            val pace = when {
                r.status == PaceStatus.ON_TRACK && r.runwayDays != null ->
                    "✅ On track. At this pace your money lasts ${r.runwayDays} more days."
                r.status == PaceStatus.OVER && r.runwayDays != null ->
                    "⚠️ Spending fast. At this pace money runs out in ${r.runwayDays} days. " +
                        "Aim for ${Money.format(r.allowancePerDayPaise.coerceAtLeast(0))}/day from now."
                r.status == PaceStatus.ON_TRACK -> "✅ On track."
                else -> null
            }
            pace?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onEditPlan, modifier = Modifier.padding(top = 4.dp)) {
                Text("Edit budget & dates", color = Color.White)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
    }
}
