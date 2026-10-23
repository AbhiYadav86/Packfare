@file:OptIn(ExperimentalMaterial3Api::class)

package com.packfare.app.ui.fares

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.domain.Destination
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.Money
import com.packfare.app.fares.FareQuote
import com.packfare.app.fares.FareResults
import com.packfare.app.fares.FareSearch
import com.packfare.app.ui.plan.formatHours
import com.packfare.app.ui.theme.HeroGradient
import com.packfare.app.util.rememberLocationRequest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FareSearchViewModel(
    private val fares: FareSearch,
    initialFrom: String?,
    initialTo: String?,
    initialDate: LocalDate?,
    private val prefs: com.packfare.app.util.Prefs? = null,
) : ViewModel() {
    var travelPref by mutableStateOf(com.packfare.app.domain.TravelPref.fromName(prefs?.travelPref))
        private set

    fun chooseTravelPref(p: com.packfare.app.domain.TravelPref) {
        travelPref = p
        prefs?.travelPref = p.name
    }

    /** Results narrowed to the preferred mode; everything when the route doesn't have it. */
    val shown: Pair<FareResults, String?>?
        get() {
            val r = results ?: return null
            val p = travelPref
            if (p == com.packfare.app.domain.TravelPref.BEST) return r to null
            val only = r.quotes.filter { it.mode in p.modes }
            return if (only.isEmpty()) {
                r to "No ${p.label.lowercase()} option on this route, so here's everything."
            } else {
                com.packfare.app.fares.FarePicker.pick(only, r.notices) to null
            }
        }

    var fromText by mutableStateOf(initialFrom.orEmpty())
    var toText by mutableStateOf(initialTo.orEmpty())
    var date by mutableStateOf(initialDate ?: LocalDate.now().plusDays(7))
    var loading by mutableStateOf(false)
        private set
    var results by mutableStateOf<FareResults?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var locationNote by mutableStateOf<String?>(null)
        private set

    val liveFlights: Boolean get() = fares.liveFlights
    val from: Destination? get() = Destinations.find(fromText)
    val to: Destination? get() = Destinations.find(toText)

    init {
        if (from != null && to != null) search()
    }

    fun swap() {
        val f = fromText
        fromText = toText
        toText = f
        results = null
    }

    fun nearMe(loc: Pair<Double, Double>?) {
        if (loc == null) {
            locationNote = "Couldn't get your location."
            return
        }
        val (d, km) = Destinations.nearest(loc.first, loc.second, 1).first()
        fromText = d.name
        locationNote = "Nearest hub: ${d.name} ($km km away)"
    }

    fun search() {
        val a = from
        val b = to
        if (a == null || b == null) {
            error = "Pick both places from the suggestions (Packfare knows ${Destinations.all.size} places)."
            return
        }
        if (a.name == b.name) {
            error = "From and To are the same place."
            return
        }
        error = null
        loading = true
        viewModelScope.launch {
            results = runCatching { fares.search(a, b, date) }
                .onFailure { error = "Search failed: ${it.message}" }
                .getOrNull()
            loading = false
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/** Feature: search flights, trains, buses and more, and surface the cheapest way to travel. */
@Composable
fun FareSearchScreen(vm: FareSearchViewModel, onBack: (() -> Unit)?) {
    var pickingDate by remember { mutableStateOf(false) }
    val requestLocation = rememberLocationRequest(vm::nearMe)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cheapest way there")
                        Text(
                            "Flights, 3AC trains & AC buses compared",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
            )
        },
        contentWindowInsets = if (onBack == null) WindowInsets(0, 0, 0, 0) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaceField("From", vm.fromText, { vm.fromText = it }, vm.from)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = requestLocation) { Text("📍 From near me") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = vm::swap) { Text("⇅ Swap") }
            }
            vm.locationNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            PlaceField("To", vm.toText, { vm.toText = it }, vm.to)

            com.packfare.app.ui.components.TravelPrefChips(vm.travelPref, vm::chooseTravelPref)

            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(vm.date.format(dateFormat))
            }

            Button(
                onClick = vm::search,
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Search fares") }

            Text(
                if (vm.liveFlights) "✈️ Live flight prices: on (Aviasales)" else "✈️ Live flight prices: off (estimates only)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (vm.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Checking fares…")
                }
            }

            vm.shown?.let { (r, note) ->
                note?.let { Text("ℹ️ $it", style = MaterialTheme.typography.bodySmall) }
                Results(r)
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = vm.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun PlaceField(label: String, value: String, onChange: (String) -> Unit, resolved: Destination?) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                when {
                    resolved != null -> Text(
                        listOfNotNull(resolved.region, resolved.nearestAirport?.let { "✈️ $it" }, if (resolved.rail) "🚆 rail" else null)
                            .joinToString(" · "),
                    )
                    value.isNotBlank() -> Text("Pick a place from the list")
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        if (resolved == null || resolved.name != value) {
            Destinations.search(value, limit = 5).filter { it.name != value }.forEach { d ->
                Text(
                    "📍 ${d.name}  ·  ${d.region}",
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onChange(d.name) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Results(r: FareResults) {
    r.notices.forEach { Text("ℹ️ $it", style = MaterialTheme.typography.bodySmall) }

    r.cheapest?.let { c ->
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroGradient).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cheapest", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(
                    "${c.mode.emoji} ${if (c.live) "" else "≈ "}${Money.format(c.pricePaise)}",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(c.title + (c.hours?.let { " · ${formatHours(it)}" } ?: ""), color = Color.White)
                r.bestValue?.takeIf { it != c }?.let {
                    Text(
                        "Best value: ${it.mode.emoji} ${it.title} ${Money.format(it.pricePaise)}" +
                            (it.hours?.let { h -> " · ${formatHours(h)}" } ?: ""),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                r.fastest?.takeIf { it != c }?.let {
                    Text(
                        "Fastest: ${it.mode.emoji} ${it.title} ${Money.format(it.pricePaise)} · ${formatHours(it.hours ?: 0.0)}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    Text("All options (per person, one way)", style = MaterialTheme.typography.titleSmall)
    r.quotes.forEach { QuoteCard(it) }
    Text(
        "Live = real fares found online recently (re-check before paying). ≈ = Packfare estimate; tap to check " +
            "live prices and seats on IRCTC, redBus or Google.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun QuoteCard(q: FareQuote) {
    val uri = LocalUriHandler.current
    Card(
        onClick = { q.url?.let { uri.openUri(it) } },
        modifier = Modifier.fillMaxWidth(),
        colors = if (q.live) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(q.mode.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(q.title, fontWeight = FontWeight.Medium)
                val details = listOfNotNull(
                    if (q.live) "LIVE · ${q.provider}" else "Estimate",
                    q.departure,
                    q.hours?.let { formatHours(it) },
                    q.note,
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (q.url != null) {
                    Text(
                        if (q.live) "Tap to book" else "Tap to check live prices",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                (if (q.live) "" else "≈ ") + Money.format(q.pricePaise),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
