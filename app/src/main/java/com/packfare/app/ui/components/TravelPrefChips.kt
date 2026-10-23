@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.packfare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.packfare.app.domain.TravelPref

/** "How would you like to travel?" ⭐ Best value / ✈️ Air / 🚆 Train / 🚌 Bus */
@Composable
fun TravelPrefChips(selected: TravelPref, onSelect: (TravelPref) -> Unit, title: String? = "How would you like to travel?") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TravelPref.entries.forEach { p ->
                FilterChip(selected = selected == p, onClick = { onSelect(p) }, label = { Text("${p.emoji} ${p.label}") })
            }
        }
        Text(
            when (selected) {
                TravelPref.BEST -> "Packfare weighs price, time and comfort for each leg."
                TravelPref.AIR -> "Flights wherever both ends have an airport (incl. getting to and from it)."
                TravelPref.TRAIN -> "AC trains wherever there's a railway (3AC, 2AC or 1AC to suit your style); road where there isn't."
                TravelPref.BUS -> "AC buses (shared jeeps in the hills); trains or flights for very long hops."
            } + " Nothing below 3AC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
