@file:OptIn(ExperimentalMaterial3Api::class)

package com.packfare.app.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packfare.app.data.MemberEntity
import com.packfare.app.domain.Category
import com.packfare.app.domain.Money
import com.packfare.app.domain.PaymentMethod
import com.packfare.app.ui.components.Avatar
import com.packfare.app.ui.components.CategoryBadge
import com.packfare.app.ui.components.CategoryDonut
import com.packfare.app.ui.theme.BalanceColors
import com.packfare.app.ui.theme.HeroGradient
import com.packfare.app.util.Upi
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------- Expenses ----------------

@Composable
internal fun ExpensesTab(
    state: TripDetailState,
    onGoToPeople: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onDeleteSettlement: (Long) -> Unit,
) {
    if (state.members.isEmpty()) {
        CenteredMessage(
            emoji = "👋",
            title = "Who's coming?",
            body = "Add the people on this trip first, then start logging expenses.",
            action = { Button(onClick = onGoToPeople) { Text("Add people") } },
        )
        return
    }

    var pendingDelete by remember { mutableStateOf<ExpenseRow?>(null) }
    val byDay = remember(state.expenses) {
        state.expenses.groupBy { dayOf(it.expense.createdAt) }.toList()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val parts = PaymentMethod.entries.mapNotNull { m ->
                state.spentByMethod[m]?.let { "${m.emoji} ${Money.format(it)}" }
            }
            Text(
                "Spent ${Money.format(state.spentPaise)}" + if (parts.isEmpty()) "" else "  ·  " + parts.joinToString("  "),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (state.expenses.isEmpty()) {
            item {
                Text(
                    "No expenses yet. Tap 🧾 to scan your first bill, or \"Add expense\" to type it in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        byDay.forEach { (day, rows) ->
            item(key = "day-$day") {
                Text(
                    dayLabel(day),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
                )
            }
            items(rows, key = { it.expense.id }) { row ->
                ExpenseItem(
                    row = row,
                    onClick = { if (!row.expense.isSettlement) onEditExpense(row.expense.id) },
                    onDeleteSettlement = { pendingDelete = row },
                )
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Undo this settlement?") },
            text = { Text("${row.paidByName} → ${row.paidToName}: ${Money.format(row.expense.amountPaise)} will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSettlement(row.expense.id)
                    pendingDelete = null
                }) { Text("Remove", color = BalanceColors.negative) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExpenseItem(row: ExpenseRow, onClick: () -> Unit, onDeleteSettlement: () -> Unit) {
    val e = row.expense
    Card(
        onClick = onClick,
        colors = if (e.isSettlement) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        ListItem(
            leadingContent = {
                if (e.isSettlement) {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                        Text("🤝", style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    CategoryBadge(Category.fromName(e.category))
                }
            },
            headlineContent = {
                Text(
                    if (e.isSettlement) "${row.paidByName} paid ${row.paidToName ?: "?"}" else e.title,
                    maxLines = 1,
                )
            },
            supportingContent = {
                val text = if (e.isSettlement) {
                    "Settlement"
                } else {
                    val split = if (row.splitCount == 1) "for 1" else "split ${row.splitCount} ways"
                    val method = PaymentMethod.fromName(e.paymentMethod).emoji
                    "$method ${row.paidByName} paid · $split" + if (e.receiptPath != null) " · 🧾" else ""
                }
                Text(text, maxLines = 1)
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Money.format(e.amountPaise), fontWeight = FontWeight.SemiBold)
                    if (e.isSettlement) {
                        IconButton(onClick = onDeleteSettlement) {
                            Icon(Icons.Filled.Delete, contentDescription = "Undo settlement")
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

private fun dayOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dayFormat)
    }
}

// ---------------- Settle up ----------------

@Composable
internal fun BalancesTab(state: TripDetailState, onMarkSettled: (SettlementRow) -> Unit) {
    val context = LocalContext.current
    var pendingSettle by remember { mutableStateOf<SettlementRow?>(null) }

    if (state.members.isEmpty()) {
        CenteredMessage("🤝", "Nothing to settle", "Add people and expenses to see who owes whom.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle("Who pays whom") }

        if (state.settlements.isEmpty()) {
            item {
                Text(
                    if (state.expenses.isEmpty()) "No expenses yet." else "🎉 Everyone is settled up. Hisaab barabar!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.settlements, key = { "${it.from.id}-${it.to.id}" }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(s.from.name, s.from.id, size = 32.dp)
                        Text("  →  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Avatar(s.to.name, s.to.id, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${s.from.name} pays ${s.to.name}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                Money.format(s.amountPaise),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Row(Modifier.padding(top = 12.dp)) {
                        val upi = s.to.upiId
                        if (upi != null) {
                            Button(onClick = {
                                Upi.launchPayment(
                                    context = context,
                                    payeeUpiId = upi,
                                    payeeName = s.to.name,
                                    amountPaise = s.amountPaise,
                                    note = "Packfare: ${state.trip?.name ?: "trip"}",
                                )
                            }) { Text("Pay via UPI") }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(onClick = { pendingSettle = s }) { Text("Mark as paid") }
                    }
                    if (s.to.upiId == null) {
                        Text(
                            "Add ${s.to.name}'s UPI ID in People to pay from the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        item { SectionTitle("Balances", Modifier.padding(top = 16.dp)) }

        items(state.balances, key = { it.member.id }) { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(b.member.name, b.member.id, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(b.member.name, Modifier.weight(1f))
                val (text, color) = when {
                    b.netPaise > 0 -> "gets back ${Money.format(b.netPaise)}" to BalanceColors.positive
                    b.netPaise < 0 -> "owes ${Money.format(-b.netPaise)}" to BalanceColors.negative
                    else -> "settled" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(text, color = color, fontWeight = FontWeight.Medium)
            }
        }
    }

    pendingSettle?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingSettle = null },
            title = { Text("Mark as paid?") },
            text = { Text("Record that ${s.from.name} paid ${s.to.name} ${Money.format(s.amountPaise)}.") },
            confirmButton = {
                TextButton(onClick = {
                    onMarkSettled(s)
                    pendingSettle = null
                }) { Text("Yes, paid") }
            },
            dismissButton = { TextButton(onClick = { pendingSettle = null }) { Text("Cancel") } },
        )
    }
}

// ---------------- People ----------------

@Composable
internal fun PeopleTab(state: TripDetailState, onPersonClick: (MemberEntity) -> Unit) {
    if (state.members.isEmpty()) {
        CenteredMessage("🧑‍🤝‍🧑", "No one here yet", "Tap \"Add person\" to add yourself and your travel buddies.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp)) {
        items(state.balances, key = { it.member.id }) { b ->
            val m = b.member
            ListItem(
                modifier = Modifier.clickable { onPersonClick(m) },
                leadingContent = { Avatar(m.name, m.id) },
                headlineContent = { Text(if (m.id == state.trip?.ownerMemberId) "${m.name} (me)" else m.name) },
                supportingContent = {
                    Text("Paid ${Money.format(b.paidPaise)} · Share ${Money.format(b.sharePaise)}")
                },
                trailingContent = {
                    Text(
                        m.upiId ?: "No UPI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        item {
            Text(
                "Tap a person to edit their name or UPI ID.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
internal fun PersonDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
    initialName: String = "",
    initialUpi: String = "",
    onRemove: (() -> Unit)? = null,
    onSetMe: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var upi by remember { mutableStateOf(initialUpi) }
    val upiOk = upi.isBlank() || Upi.isValidId(upi)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = upi,
                    onValueChange = { upi = it },
                    label = { Text("UPI ID (optional)") },
                    placeholder = { Text("name@okaxis") },
                    singleLine = true,
                    isError = !upiOk,
                    supportingText = { if (!upiOk) Text("Should look like name@bank") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                if (onSetMe != null) {
                    TextButton(onClick = onSetMe) { Text("This is me (track my cash)") }
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove from trip", color = BalanceColors.negative)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, upi.ifBlank { null }) },
                enabled = name.isNotBlank() && upiOk,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- Shared bits ----------------

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
internal fun CenteredMessage(emoji: String, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}
