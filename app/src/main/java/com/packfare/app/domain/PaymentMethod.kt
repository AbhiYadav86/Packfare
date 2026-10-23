package com.packfare.app.domain

enum class PaymentMethod(val label: String, val emoji: String) {
    CASH("Cash", "💵"),
    UPI("UPI", "📲"),
    CARD("Card", "💳");

    companion object {
        fun fromName(name: String?): PaymentMethod = entries.firstOrNull { it.name == name } ?: UPI
    }
}
