@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packfare.app.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.packfare.app.data.TripEntity
import com.packfare.app.domain.Money
import com.packfare.app.domain.TravelStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Budget, length, start date and travel style of a trip. */
@Composable
internal fun EditPlanDialog(
    trip: TripEntity,
    onDismiss: () -> Unit,
    onSave: (budgetPaise: Long, days: Int, start: LocalDate?, style: TravelStyle) -> Unit,
) {
    var budget by remember {
        mutableStateOf(trip.budgetPaise.takeIf { it > 0 }?.let { Money.toUpiAmount(it).removeSuffix(".00") } ?: "")
    }
    var days by remember { mutableStateOf(trip.plannedDays.takeIf { it > 0 }?.toString() ?: "") }
    var start by remember { mutableStateOf(trip.startEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    var style by remember { mutableStateOf(TravelStyle.fromName(trip.style)) }
    var picking by remember { mutableStateOf(false) }

    val budgetPaise = if (budget.isBlank()) 0L else Money.parse(budget)
    val dayCount = days.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget & dates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = budget, onValueChange = { budget = it },
                    label = { Text("Total budget") }, prefix = { Text("₹ ") }, singleLine = true,
                    isError = budgetPaise == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit).take(3) },
                    label = { Text("Number of days") }, singleLine = true,
                    supportingText = {
                        if (budgetPaise != null && budgetPaise > 0 && dayCount != null && dayCount > 0) {
                            Text("≈ ${Money.format(budgetPaise / dayCount)} a day")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                TextButton(onClick = { picking = true }) {
                    Text("Starts ${start.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))}")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TravelStyle.entries.forEach { s ->
                        FilterChip(selected = style == s, onClick = { style = s }, label = { Text(s.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = budgetPaise != null && (dayCount ?: 0) > 0,
                onClick = { onSave(budgetPaise ?: 0L, dayCount ?: 1, start, style) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { start = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Log an ATM withdrawal (or currency exchange) into the cash wallet. */
@Composable
internal fun CashDialog(onDismiss: () -> Unit, onSave: (Long, String?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val paise = Money.parse(amount)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdrew cash") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Where? (optional)") }, placeholder = { Text("SBI ATM, Kaza") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = paise != null, onClick = { paise?.let { onSave(it, note) } }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
