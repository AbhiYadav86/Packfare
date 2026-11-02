package com.packfare.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packfare.app.domain.TripSearch
import com.packfare.app.ui.components.HomeBackdrop

/**
 * The front page: one question, your unfinished plan, and four quick starts. The compass is drawn
 * behind this page (and every other screen); the quick starts sit over its middle.
 */
@Composable
fun HomeLanding(
    draft: TripSearch?,
    onSearch: () -> Unit,
    onContinue: () -> Unit,
    onScanBill: () -> Unit,
    onNewEvent: () -> Unit,
    onFares: () -> Unit,
    onHowLong: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        HomeBackdrop()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("PACKFARE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Where to next?", fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
            }
            Card(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text("🔎", fontSize = 18.sp)
                    Text(
                        "  Search places, or try “beach in November”",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (draft != null) {
                Card(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CONTINUE PLANNING", style = MaterialTheme.typography.labelMedium)
                        Text(draft.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(draft.subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                }
            }
            // Space so the quick starts land across the middle of the screen, over the compass.
            Spacer(Modifier.height(if (draft != null) 24.dp else 96.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("🧾", "Scan a bill", "File it under a trip or event", onScanBill, Modifier.weight(1f))
                QuickTile("🎫", "New event", "Conference, client visit", onNewEvent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("🚆", "Compare fares", "Flights, trains, buses", onFares, Modifier.weight(1f))
                QuickTile("🧮", "How long?", "Turn money into days", onHowLong, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickTile(emoji: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 26.sp)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
