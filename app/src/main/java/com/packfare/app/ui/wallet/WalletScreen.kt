@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.packfare.app.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packfare.app.ui.trips.BillFlow
import com.packfare.app.ui.trips.ScanBillCard
import com.packfare.app.ui.trips.TripListViewModel
import com.packfare.app.ui.vault.VaultScreen
import com.packfare.app.ui.vault.VaultViewModel

/** Money and papers: bills (scan, events, reimbursement) and documents (the vault). */
@Composable
fun WalletScreen(
    tripsVm: TripListViewModel,
    vaultVm: VaultViewModel,
    myName: String,
    onScanBill: (Long) -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenDoc: (Long) -> Unit,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding()) {
            Text("Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
            TabRow(selectedTabIndex = section, containerColor = Color.Transparent) {
                Tab(selected = section == 0, onClick = { section = 0 }, text = { Text("Bills") })
                Tab(selected = section == 1, onClick = { section = 1 }, text = { Text("Documents") })
            }
        }
        if (section == 0) {
            BillFlow(tripsVm, myName, onScanBill, onOpenTrip) { scan, newEvent ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScanBillCard(onClick = scan)
                    OutlinedButton(onClick = newEvent, modifier = Modifier.fillMaxWidth()) {
                        Text("🎫  New event (conference, client visit, offsite)")
                    }
                    Text(
                        "To claim back an event's costs, open it from Trips and tap the 📄 button for a reimbursement PDF " +
                            "with every bill and category subtotals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            VaultScreen(vaultVm, onOpen = onOpenDoc, showTopBar = false)
        }
    }
}
