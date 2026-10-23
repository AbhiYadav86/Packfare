package com.packfare.app.ui.trip

import android.content.Context
import android.content.Intent
import com.packfare.app.domain.Money
import com.packfare.app.ui.components.emoji

/** Plain-text trip summary that reads well in WhatsApp (uses *bold* markup). */
fun buildShareText(state: TripDetailState): String = buildString {
    val trip = state.trip ?: return@buildString
    val people = state.members.size
    appendLine("🧳 *${trip.name}*" + if (trip.destination.isNotBlank()) " · ${trip.destination}" else "")
    appendLine()
    append("Total spent: *${Money.format(state.spentPaise)}*")
    if (people > 0) append(" (≈ ${Money.format(state.spentPaise / people)} per person)")
    appendLine()
    if (trip.budgetPaise > 0) {
        val left = trip.budgetPaise - state.spentPaise
        appendLine(
            "Budget: ${Money.format(trip.budgetPaise)} · " +
                if (left >= 0) "${Money.format(left)} left" else "${Money.format(-left)} over",
        )
    }

    if (state.spentByCategory.isNotEmpty()) {
        appendLine()
        appendLine("*Where it went*")
        state.spentByCategory.forEach { (cat, amount) ->
            appendLine("${cat.emoji} ${cat.label}: ${Money.format(amount)}")
        }
    }

    appendLine()
    if (state.settlements.isEmpty()) {
        appendLine("✅ Everyone is settled up!")
    } else {
        appendLine("*Settle up*")
        state.settlements.forEach { s ->
            val upi = s.to.upiId?.let { " (UPI: $it)" }.orEmpty()
            appendLine("• ${s.from.name} → ${s.to.name}: ${Money.format(s.amountPaise)}$upi")
        }
    }
    appendLine()
    append("Tracked with Packfare")
}

/** Opens the share sheet (WhatsApp, Telegram, SMS...). */
fun shareTrip(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share trip summary"))
}
