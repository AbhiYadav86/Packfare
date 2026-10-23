@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packfare.app.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.TextButton
import com.packfare.app.domain.FareEstimator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.packfare.app.domain.CostTier
import com.packfare.app.ui.components.MapPin
import com.packfare.app.ui.components.PackfareMap
import com.packfare.app.ui.components.PinColors
import com.packfare.app.util.rememberLocationRequest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packfare.app.domain.Destination
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.Money
import com.packfare.app.domain.TravelStyle

@Composable
fun ExploreScreen(vm: ExploreViewModel, onOpenPlace: (String) -> Unit) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    var region by rememberSaveable { mutableStateOf(Destinations.regions.first()) }
    val requestLocation = rememberLocationRequest(vm::onLocation)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Explore")
                        Text(
                            "Search any place in India for real backpacker prices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Hampi, Spiti, Ziro, Gokarna…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )
            }

            item {
                var picking by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        "Fares from",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = { picking = true },
                        label = { Text(vm.home?.let { "🏠 ${it.name}" } ?: "Set your home city") },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelStyle.entries.forEach { s ->
                        FilterChip(selected = vm.style == s, onClick = { vm.style = s }, label = { Text(s.label) })
                    }
                }
                if (vm.home != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.packfare.app.domain.TravelPref.entries.forEach { p ->
                            FilterChip(
                                selected = vm.travelPref == p,
                                onClick = { vm.chooseTravelPref(p) },
                                label = { Text("${p.emoji} ${p.label}") },
                            )
                        }
                    }
                }
                if (picking) {
                    CityPickerDialog(
                        title = "Where do your trips start?",
                        onPick = { vm.pickHome(it); picking = false },
                        onClear = if (vm.home != null) ({ vm.pickHome(null); picking = false }) else null,
                        onDismiss = { picking = false },
                    )
                }
            }

            if (vm.query.isBlank()) {
                item {
                    val pins = remember {
                        Destinations.all.map { d ->
                            MapPin(
                                id = d.wikivoyage,
                                lat = d.lat,
                                lon = d.lon,
                                color = when (d.tier) {
                                    CostTier.LOW -> PinColors.low
                                    CostTier.MID -> PinColors.mid
                                    CostTier.HIGH -> PinColors.high
                                },
                                label = d.name,
                                radius = if (d.hub) 6f else 3.5f,
                            )
                        }
                    }
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        PackfareMap(
                            pins = pins,
                            myLocation = vm.myLocation,
                            focus = vm.myLocation?.let { me -> listOf(me) + vm.nearby.take(3).map { it.first.lat to it.first.lon } },
                            onPinClick = onOpenPlace,
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        FilledTonalButton(onClick = requestLocation) { Text("📍 Near me") }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "🟢 cheap  🟠 mid  🔴 pricey · tap a dot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    vm.locationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                if (vm.nearby.isNotEmpty()) {
                    item { Header("Closest to you") }
                    items(vm.nearby, key = { "near-${it.first.name}" }) { (d, km) ->
                        PlaceRow(
                            title = d.name,
                            subtitle = "${d.region} · $km km away",
                            trailing = "≈ ${Money.format(d.baseline.forStyle(vm.style).totalPaise)}/day",
                            onClick = { onOpenPlace(d.wikivoyage) },
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    item { Header("Saved for offline") }
                    items(recent, key = { "recent-${it.title}" }) { p ->
                        PlaceRow(
                            title = p.title,
                            subtitle = p.summary.take(90).ifBlank { "Saved place" },
                            onClick = { onOpenPlace(p.title) },
                        )
                    }
                }

                item { Header("Places by region (${Destinations.all.size} in all)") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Destinations.regions.forEach { r ->
                            FilterChip(selected = region == r, onClick = { region = r }, label = { Text(r) })
                        }
                    }
                }
                val (hubs, towns) = Destinations.inRegion(region).partition { it.hub }
                items(hubs, key = { "hub-${it.name}" }) { d ->
                    DestinationRow(d, vm.home, vm.style, vm.travelPref, onClick = { onOpenPlace(d.wikivoyage) })
                }
                if (towns.isNotEmpty()) {
                    item { Header("More towns & sights in $region") }
                    items(towns, key = { "town-${it.name}" }) { d ->
                        DestinationRow(d, vm.home, vm.style, vm.travelPref, onClick = { onOpenPlace(d.wikivoyage) })
                    }
                }
            } else {
                if (vm.localHits.isNotEmpty()) {
                    item { Header("In Packfare's guide (works offline)") }
                    items(vm.localHits, key = { "local-${it.name}" }) { d ->
                        DestinationRow(d, vm.home, vm.style, vm.travelPref, onClick = { onOpenPlace(d.wikivoyage) })
                    }
                }
                item {
                    Header(if (vm.searching) "Searching Wikivoyage…" else "On Wikivoyage")
                }
                if (vm.searching) {
                    item { CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp), strokeWidth = 3.dp) }
                }
                vm.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
                items(vm.webHits, key = { "web-${it.title}" }) { hit ->
                    PlaceRow(title = hit.title, subtitle = hit.snippet, onClick = { onOpenPlace(hit.title) })
                }
                if (!vm.searching && vm.webHits.isEmpty() && vm.query.trim().length >= 3 && vm.error == null) {
                    item { Text("No articles found.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DestinationRow(d: Destination, home: Destination?, style: TravelStyle, pref: com.packfare.app.domain.TravelPref, onClick: () -> Unit) {
    val perDay = d.baseline.forStyle(style).totalPaise
    val flags = listOfNotNull(
        if (d.fewAtms) "💵 few ATMs" else null,
        if (d.permitNote != null) "📜 permit" else null,
        if (style.rooms) "room ≈ ${Money.format(d.baseline.roomPaise(style))}" else null,
    ).joinToString(" · ")
    val fares = remember(d.name, home?.name, pref) { home?.let { FareEstimator.fromHome(it, d, pref) } }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(d.name) },
        supportingContent = {
            Column {
                Text(
                    listOf(d.region, flags).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fares != null && home != null) {
                    Text(
                        "From ${home.name}: " + homeFaresLine(fares),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = { Text("≈ ${Money.format(perDay)}/day", style = MaterialTheme.typography.labelLarge) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** "✈️ ≈₹5,400 · 4h · 🚆 ≈₹900 · 20h" */
internal fun homeFaresLine(f: FareEstimator.HomeFares): String =
    listOfNotNull(f.flight, f.ground).joinToString("   ") { o ->
        "${o.mode.emoji} ≈${Money.format(o.farePaise)} · ${com.packfare.app.ui.plan.formatHours(o.hours)}"
    }

/** Search-as-you-type picker over Packfare's places. */
@Composable
internal fun CityPickerDialog(
    title: String,
    onPick: (Destination) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }
    val hits = remember(text) { Destinations.search(text, 6) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Bengaluru, Ahmedabad, Delhi…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                hits.forEach { d ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(d) },
                        headlineContent = { Text(d.name) },
                        supportingContent = { Text(d.region) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (onClear != null) TextButton(onClick = onClear) { Text("Clear") }
        },
    )
}

@Composable
private fun PlaceRow(title: String, subtitle: String, onClick: () -> Unit, trailing: String? = null) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
