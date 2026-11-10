@file:OptIn(ExperimentalLayoutApi::class)

package com.packfare.app.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.packfare.app.data.TripEntity
import com.packfare.app.domain.BookingItem
import com.packfare.app.domain.Destination
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.HotelBudget
import com.packfare.app.domain.ItineraryDay
import com.packfare.app.domain.Money
import com.packfare.app.domain.TravelStyle
import com.packfare.app.explore.PriceSample
import com.packfare.app.fares.BookingLinks
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * Hotels for each stop of the trip: Booking.com and Agoda links (Booking.com filtered to your nightly
 * budget), and the guide's priced places to sleep, with the ones that fit your budget highlighted.
 */
@Composable
fun HotelsTab(
    trip: TripEntity?,
    plan: List<ItineraryDay>,
    people: Int,
    style: TravelStyle,
    stays: Map<String, List<PriceSample>>,
    onLoadStays: (List<Destination>) -> Unit,
) {
    val uri = LocalUriHandler.current
    val items = BookingItem.decode(trip?.bookings)
    val names = trip?.destination.orEmpty().split("→").map { it.trim() }.filter { it.isNotBlank() }
    val places = names.mapNotNull { Destinations.find(it) }.distinctBy { it.name }

    LaunchedEffect(places.map { it.name }) { onLoadStays(places) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Hotels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Prices from the guide are a starting point; the booking sites show today's rooms. " +
                "Places that fit your nightly budget are highlighted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (places.isEmpty()) {
            Text("Add a place to this trip (pencil icon) to see hotels.", style = MaterialTheme.typography.bodyMedium)
        }
        places.forEachIndexed { i, place ->
            val days = plan.filter { it.place == place.name }
            val checkIn = days.firstOrNull()?.date ?: LocalDate.now().plusDays(7)
            val nights = days.size.coerceAtLeast(1)
            val checkOut = checkIn.plusDays(nights.toLong())
            val units = HotelBudget.units(people, style.rooms, sharing = people > 1)
            val planned = items.firstOrNull { it.key == "stay$i" }?.plannedPaise
            val defaultCap = if (planned != null && planned > 0) HotelBudget.nightlyCapPaise(planned, nights, units)
            else if (style.rooms) place.baseline.roomPaise(style) else place.baseline.stayPaise
            var capText by remember(place.name, defaultCap) { mutableStateOf((defaultCap / 100).toString()) }
            val cap = (Money.parse(capText) ?: defaultCap).coerceAtLeast(0L)
            val unitLabel = if (style.rooms) "room" else "bed"
            val rooms = (people + 1) / 2

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${checkIn.format(shortDay)} – ${checkOut.format(shortDay)} · $nights night${if (nights == 1) "" else "s"} · " +
                            "$people ${if (people == 1) "traveller" else "travellers"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = capText,
                        onValueChange = { capText = it },
                        singleLine = true,
                        label = { Text("My budget per $unitLabel per night") },
                        prefix = { Text("₹ ") },
                        supportingText = {
                            Text(if (planned != null && planned > 0) "Starts from what your plan set aside for this stay." else "Starts from the typical price for ${style.label}.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { uri.openUri(BookingLinks.bookingComUnder(place, checkIn, checkOut, people, rooms, cap)) },
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Booking.com") }
                        OutlinedButton(
                            onClick = { uri.openUri(BookingLinks.agoda(place, checkIn, checkOut, people, rooms)) },
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Agoda") }
                        OutlinedButton(
                            onClick = { uri.openUri(BookingLinks.googleHotels(place, hostels = !style.rooms)) },
                            shape = RoundedCornerShape(10.dp),
                        ) { Text(if (style.rooms) "Google Hotels" else "Hostels") }
                    }
                    Text(
                        "Booking.com opens already filtered to ${Money.format(cap)} a night. Agoda has no budget filter in its link, " +
                            "so set the price slider there.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val listings = stays[place.name].orEmpty().filter { style.rooms.not() || !it.isDorm }.take(10)
                    if (listings.isNotEmpty()) {
                        Text("From the guide", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                        listings.forEach { l ->
                            val fits = HotelBudget.fits(l.pricePaise, cap)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (fits) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.name + if (l.isDorm) " (dorm)" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = if (fits) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(
                                        if (fits) "✓ Fits your budget" else "${Money.format(l.pricePaise - cap)} over budget",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (fits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(Money.format(l.pricePaise), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Text(
                            if (stays.containsKey(place.name)) "The guide lists no prices for places to sleep here. Use the booking links."
                            else "Looking up places to sleep…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Text(
                "Links open the booking sites. Packfare may earn a commission on bookings made through them once its " +
                    "affiliate accounts are set up; it doesn't change your price.",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
