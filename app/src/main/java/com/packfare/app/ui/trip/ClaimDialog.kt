package com.packfare.app.ui.trip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.packfare.app.domain.ClaimLine
import com.packfare.app.domain.ClaimSummary
import com.packfare.app.domain.Category
import com.packfare.app.domain.Money
import com.packfare.app.domain.PaymentMethod
import com.packfare.app.report.ClaimHeader
import java.time.Instant
import java.time.ZoneId

/** Asks who is claiming from which company, then builds the reimbursement PDF for this trip or event. */
@Composable
internal fun ClaimDialog(
    state: TripDetailState,
    initialClaimant: String,
    initialCompany: String,
    onDismiss: () -> Unit,
    onCreate: (header: ClaimHeader, summary: ClaimSummary, company: String) -> Unit,
) {
    val trip = state.trip ?: return
    var claimant by remember { mutableStateOf(initialClaimant) }
    var company by remember { mutableStateOf(initialCompany) }
    var onlyMine by remember { mutableStateOf(true) }
    val meId = trip.ownerMemberId

    fun lines(): List<ClaimLine> = state.expenses
        .filter { !it.expense.isSettlement && (!onlyMine || meId == null || it.expense.paidById == meId) }
        .map {
            val e = it.expense
            ClaimLine(
                epochDay = Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                vendor = e.title,
                category = Category.fromName(e.category),
                paymentMethod = PaymentMethod.fromName(e.paymentMethod),
                amountPaise = e.amountPaise,
                hasReceipt = e.receiptPath != null,
            )
        }

    val preview = ClaimSummary.build(lines())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reimbursement claim (PDF)") },
        text = {
            Column {
                OutlinedTextField(
                    value = claimant,
                    onValueChange = { claimant = it },
                    label = { Text("Claimant name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / cost centre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = onlyMine, onCheckedChange = { onlyMine = it })
                    Text("Only bills I paid", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (preview.lines.isEmpty()) "No bills to include yet."
                    else "${preview.lines.size} bill(s), total ${Money.format(preview.totalPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = preview.lines.isNotEmpty(),
                onClick = {
                    val s = preview
                    val from = s.lines.minOfOrNull { it.epochDay }
                    val to = s.lines.maxOfOrNull { it.epochDay }
                    val ref = ClaimSummary.reference(trip.id, java.time.LocalDate.now().toEpochDay())
                    onCreate(
                        ClaimHeader(
                            reference = ref,
                            eventName = trip.name,
                            place = trip.destination,
                            claimant = claimant.trim(),
                            company = company.trim(),
                            periodFrom = from?.let { java.time.LocalDate.ofEpochDay(it) },
                            periodTo = to?.let { java.time.LocalDate.ofEpochDay(it) },
                        ),
                        s,
                        company.trim(),
                    )
                },
            ) { Text("Create PDF") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
