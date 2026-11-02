@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packfare.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.packfare.app.domain.BudgetPlanner
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.Money
import com.packfare.app.domain.TravelStyle
import com.packfare.app.ui.theme.HeroGradient

/**
 * Feature 2, the reverse planner: "I have ₹X. How long can I travel in this region?"
 * Pure on-device math, no network.
 */
@Composable
fun HowLongScreen(onBack: () -> Unit, onPlan: (stops: List<String>, days: Int, budgetPaise: Long) -> Unit) {
    var budget by rememberSaveable { mutableStateOf("30000") }
    var region by rememberSaveable { mutableStateOf("Himachal") }
    var styleName by rememberSaveable { mutableStateOf(TravelStyle.BACKPACKER.name) }
    val style = TravelStyle.fromName(styleName)
    val total = Money.parse(budget) ?: 0L
    val places = Destinations.hubsIn(region)
    val result = BudgetPlanner.reverse(total, places, style)

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("How long can I travel?")
                        Text(
                            "Your money, turned into days on the road",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = budget,
                onValueChange = { budget = it },
                label = { Text("Money for the trip (per person)") },
                prefix = { Text("₹ ") },
                singleLine = true,
                isError = budget.isNotBlank() && total <= 0,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Where?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Destinations.regions.forEach { r ->
                        FilterChip(selected = region == r, onClick = { region = r }, label = { Text(r) })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How do you travel?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelStyle.entries.forEach { s ->
                        FilterChip(selected = style == s, onClick = { styleName = s.name }, label = { Text(s.label) })
                    }
                }
            }

            if (result != null) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroGradient).padding(20.dp)) {
                    Column {
                        Text("You can travel about", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${result.days} days",
                            color = Color.White,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("in $region as a ${style.label.lowercase()}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "≈ ${Money.format(result.perDayPaise)}/day, keeping ${Money.format(result.reservedForTravelPaise)} " +
                                "(15%) for long-distance buses and trains.",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Text("Daily cost in $region", style = MaterialTheme.typography.titleSmall)
                places.sortedBy { it.baseline.totalPaise }.forEach { d ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(d.name + if (d.fewAtms) "  💵" else "", Modifier.weight(1f))
                        Text("≈ ${Money.format(d.baseline.forStyle(style).totalPaise)}/day")
                    }
                }

                Button(
                    onClick = {
                        val picks = places.sortedBy { it.baseline.totalPaise }.take(3).map { it.name }
                        onPlan(picks, result.days.coerceAtLeast(1), total)
                    },
                    enabled = result.days > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Plan this trip") }
            } else {
                Text("Enter an amount to see how far it goes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
