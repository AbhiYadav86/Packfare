package com.packfare.app.domain

/**
 * How someone travels, from dorm beds to 4-star hotels.
 *
 * Stay: BACKPACKER uses per-bed dorm prices; the others use per-room hotel prices
 * (shared by two, or paid in full when travelling solo). Food, local transport and sights
 * scale the backpacker baseline by the multipliers below.
 */
enum class TravelStyle(
    val label: String,
    val blurb: String,
    /** True when stays are hotel rooms (priced per room), false for dorm beds (per person). */
    val rooms: Boolean,
    val food: Double,
    val local: Double,
    val activities: Double,
    /** What an hour of travel is worth when picking a route (₹, in paise). Higher = prefers faster. */
    val timeValuePaisePerHour: Long,
    /** Avoids non-AC sleeper trains, ordinary buses and shared jeeps when there's a decent alternative. */
    val prefersAc: Boolean,
) {
    BACKPACKER("Backpacker", "Dorm beds, dhabas & cafés, local buses", false, 1.0, 1.0, 1.0, 6_000, false),
    FLASHPACKER("Budget hotel", "1–2★ private rooms, cafés, AC buses & autos", true, 1.6, 1.4, 1.5, 15_000, false),
    COMFORT("3★ comfort", "3-star hotels, restaurants, AC trains & cabs", true, 2.2, 2.2, 2.0, 30_000, true),
    PREMIUM("4★ premium", "4-star hotels, good restaurants, flights & cabs", true, 3.0, 3.0, 2.5, 60_000, true);

    companion object {
        fun fromName(name: String?): TravelStyle = entries.firstOrNull { it.name == name } ?: BACKPACKER
    }
}
