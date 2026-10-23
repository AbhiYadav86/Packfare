@file:OptIn(ExperimentalMaterial3Api::class)

package com.packfare.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packfare.app.data.TripSummary
import com.packfare.app.domain.Money
import com.packfare.app.domain.PaceStatus
import com.packfare.app.domain.Runway
import java.time.LocalDate
import com.packfare.app.ui.components.BudgetBar

@Composable
fun TripListScreen(vm: TripListViewModel, onOpenTrip: (Long) -> Unit, onPlanTrip: () -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Packfare", fontWeight = FontWeight.Bold)
                        Text(
                            "Backpack India on a budget",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPlanTrip,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Plan a trip") },
            )
        },
    ) { padding ->
        val list = trips
        when {
            list == null -> Unit // loading; Room is fast so this is a blink
            list.isEmpty() -> EmptyState(Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.trip.id }) { summary ->
                    TripCard(summary, onClick = { onOpenTrip(summary.trip.id) })
                }
            }
        }
    }

}

@Composable
private fun TripCard(summary: TripSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(destinationEmoji(summary.trip.destination + " " + summary.trip.name), fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(summary.trip.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    val people = if (summary.memberCount == 1) "1 person" else "${summary.memberCount} people"
                    val subtitle = listOf(summary.trip.destination, people).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            runwayLine(summary)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            BudgetBar(
                spentPaise = summary.spentPaise,
                budgetPaise = summary.trip.budgetPaise,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🧳", fontSize = 56.sp)
            Text("Where to next?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Plan a trip to get a suggested budget and route, then track every rupee as you go. " +
                    "Works fully offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A little emoji for the trip card, guessed from the destination. Purely decorative. */
private fun destinationEmoji(text: String): String {
    val t = text.lowercase()
    fun has(vararg words: String) = words.any { it in t }
    return when {
        has("goa", "beach", "gokarna", "andaman", "pondicherry", "puducherry", "varkala") -> "🏖️"
        has("manali", "ladakh", "leh", "shimla", "spiti", "himachal", "kashmir", "sikkim", "darjeeling", "uttarakhand", "trek") -> "🏔️"
        has("jaipur", "udaipur", "jodhpur", "jaisalmer", "rajasthan", "fort") -> "🏰"
        has("kerala", "munnar", "alleppey", "alappuzha", "coorg", "wayanad") -> "🌴"
        has("varanasi", "rishikesh", "haridwar", "tirupati", "amritsar", "shirdi", "puri") -> "🛕"
        has("mumbai", "delhi", "bangalore", "bengaluru", "kolkata", "chennai", "hyderabad", "pune") -> "🏙️"
        else -> "🧳"
    }
}

/** "Day 4 of 21 · ≈ ₹1,050/day" for trips with dates, null otherwise. */
private fun runwayLine(summary: TripSummary): String? {
    val start = summary.trip.startEpochDay ?: return null
    val r = Runway.compute(
        startDate = LocalDate.ofEpochDay(start),
        plannedDays = summary.trip.plannedDays,
        budgetPaise = summary.trip.budgetPaise,
        spentPaise = summary.spentPaise,
        todaySpentPaise = 0,
        today = LocalDate.now(),
    )
    return when (r.status) {
        PaceStatus.NOT_STARTED -> if (r.daysUntilStart == 1) "Starts tomorrow" else "Starts in ${r.daysUntilStart} days"
        PaceStatus.FINISHED -> "Trip complete"
        PaceStatus.NO_BUDGET -> "Day ${r.dayNumber} of ${r.plannedDays}"
        PaceStatus.ON_TRACK -> "Day ${r.dayNumber} of ${r.plannedDays} · ≈ ${Money.format(r.avgPerDayPaise)}/day · on track"
        PaceStatus.OVER -> "Day ${r.dayNumber} of ${r.plannedDays} · ≈ ${Money.format(r.avgPerDayPaise)}/day · over budget"
    }
}
