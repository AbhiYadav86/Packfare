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
import com.packfare.app.domain.Theme
import com.packfare.app.ui.theme.HeroGradient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * Explore is where trip planning starts ("Plan a trip" on the Trips tab lands here): search any place,
 * pick themes and trip length, get ready-made trips from home with all-in prices, or open a place and
 * plan around it. "Build your own trip" opens the full planner.
 */
@Composable
fun ExploreScreen(
    vm: ExploreViewModel,
    onOpenPlace: (String) -> Unit,
    onPlanTrip: (stops: List<Pair<String, Int>>, from: String?, style: String?) -> Unit = { _, _, _ -> },
    onBuildOwn: () -> Unit = {},
    onResume: (Int) -> Unit = {},
) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    var region by rememberSaveable { mutableStateOf(Destinations.regions.first()) }
    val requestLocation = rememberLocationRequest(vm::onLocation)
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshSaved() }

    Scaffold(
        topBar = {
            Column(
                Modifier.fillMaxWidth().background(HeroGradient).statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            ) {
                Text("Plan your next trip", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Search a place, or pick what you're into and we'll suggest trips with all-in prices",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Hampi, Spiti, Ziro, Gokarna…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color(0xFF1B211D),
                        unfocusedTextColor = Color(0xFF1B211D),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedPlaceholderColor = Color(0xFF727A73),
                        unfocusedPlaceholderColor = Color(0xFF727A73),
                        focusedLeadingIconColor = Color(0xFF2F6B4F),
                        unfocusedLeadingIconColor = Color(0xFF2F6B4F),
                    ),
                )
            }
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
            }

            if (vm.query.isBlank()) {
                item { ThemeAndLengthPicker(vm) }

                vm.draft?.let { d ->
                    item {
                        Card(
                            Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onResume(-2) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Continue planning", style = MaterialTheme.typography.labelLarge)
                                    Text(d.name.ifBlank { d.title }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(d.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Button(onClick = { onResume(-2) }) { Text("Resume") }
                            }
                        }
                    }
                }
                if (vm.recentSearches.isNotEmpty()) {
                    item { Header("Your recent searches") }
                    items(vm.recentSearches.take(3).withIndex().toList(), key = { "rs-${it.index}" }) { (i, rs) ->
                        PlaceRow(title = "🕘 ${rs.title}", subtitle = rs.subtitle, onClick = { onResume(i) })
                    }
                }

                item {
                    Header(vm.home?.let { "Suggested trips from ${it.name}" } ?: "Suggested trips")
                }
                item {
                    val home = vm.home
                    if (home == null) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Where do your trips start? We'll suggest routes with fares there and back included.")
                                Button(onClick = { picking = true }) { Text("🏠 Set your home city") }
                            }
                        }
                    } else {
                        val trips = remember(home.name, vm.tripNights, vm.themes, vm.style, vm.travelPref) { vm.suggestedTrips() }
                        if (trips.isEmpty()) {
                            Text("No trips match. Try other themes or a different length.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            trips.forEach { t ->
                                SuggestedTripCard(t, onPlan = {
                                    onPlanTrip(t.stops.map { it.destination.name to it.nights }, home.name, vm.style.name)
                                })
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = onBuildOwn, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("🧭 Build your own trip (pick every stop)")
                    }
                }

                val themed = vm.themedPlaces()
                if (themed.isNotEmpty()) {
                    item { Header("Places for ${vm.themes.joinToString { it.label.lowercase() }}") }
                    items(themed, key = { "theme-${it.name}" }) { d ->
                        DestinationRow(d, vm.home, vm.style, vm.travelPref, onClick = { onOpenPlace(d.wikivoyage) })
                    }
                }

                item { Header("Browse by price") }
                item {
                    // Which price level to browse. null = all.
                    var tier by rememberSaveable { mutableStateOf<String?>(null) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf(
                            CostTier.LOW to "🟢 Cheap",
                            CostTier.MID to "🟠 Mid",
                            CostTier.HIGH to "🔴 Pricey",
                        ).forEach { (t, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = tier == t.name,
                                onClick = { tier = if (tier == t.name) null else t.name },
                                label = { Text(label) },
                            )
                        }
                    }
                    val picks = remember(tier, vm.style) {
                        Destinations.all
                            .filter { it.hub && (tier == null || it.tier.name == tier) }
                            .sortedBy { it.baseline.forStyle(vm.style).totalPaise }
                            .take(12)
                    }
                    Text(
                        (if (tier == null) "Cheapest popular places first" else "Popular places at this level, cheapest first") +
                            " · daily cost for your travel style.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Column(Modifier.padding(top = 4.dp)) {
                        picks.forEach { d ->
                            DestinationRow(d, vm.home, vm.style, vm.travelPref, onClick = { onOpenPlace(d.wikivoyage) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        FilledTonalButton(onClick = { vm.startLocating(); requestLocation() }, enabled = !vm.locating) {
                            Text(if (vm.locating) "Finding you…" else "📍 Near me")
                        }
                        Spacer(Modifier.width(12.dp))
                        val closest = vm.nearby.firstOrNull()
                        Text(
                            if (closest != null) "Closest: ${closest.first.name}, ${closest.second} km" else "Lists the places closest to where you are",
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
                    Header(if (vm.searching) "Searching…" else "More places")
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

    if (picking) {
        CityPickerDialog(
            title = "Where do your trips start?",
            onPick = { vm.pickHome(it); picking = false },
            onClear = if (vm.home != null) ({ vm.pickHome(null); picking = false }) else null,
            onDismiss = { picking = false },
        )
    }
}

/** Themes, trip length, and the style/transport chips that shape every suggestion and price. */
@Composable
private fun ThemeAndLengthPicker(vm: ExploreViewModel) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("What are you into?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Theme.entries.forEach { t ->
                FilterChip(selected = t in vm.themes, onClick = { vm.toggleTheme(t) }, label = { Text("${t.emoji} ${t.label}") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trip length", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            FilledTonalButton(onClick = { vm.changeTripNights(-1) }, enabled = vm.tripNights > 2) { Text("−") }
            Text("${vm.tripNights} nights", Modifier.width(84.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            FilledTonalButton(onClick = { vm.changeTripNights(+1) }, enabled = vm.tripNights < 30) { Text("+") }
        }
    }
}

/** A ready-made trip: theme, route, nights and the all-in price per person. */
@Composable
private fun SuggestedTripCard(t: SuggestedTrip, onPlan: () -> Unit) {
    val b = t.budget
    Card(Modifier.fillMaxWidth().clickable(onClick = onPlan), shape = RoundedCornerShape(20.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().background(HeroGradient).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column {
                    Text("${t.theme.emoji} ${t.theme.label}", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${b.days} nights · ${t.stops.size} stop${if (t.stops.size == 1) "" else "s"}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    t.stops.joinToString("  →  ") { "${it.destination.name} (${it.nights}n)" },
                    fontWeight = FontWeight.Medium,
                )
                val transport = b.legs.map { it.option.mode }.distinct().joinToString(" ") { it.emoji }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("≈ ${Money.format(b.perPersonPaise)} per person", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "All in: $transport fares there & back, ${b.style.label.lowercase()} stays, food, sights",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onPlan) { Text("Plan this") }
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
