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
    /** Train class this style books by default: 3AC, 2AC or 1AC (never below 3AC). */
    val trainClass: TransportMode,
) {
    BACKPACKER("Backpacker", "Dorm beds, dhabas & cafés, 3AC trains & AC buses", false, 1.0, 1.0, 1.0, 6_000, false, TransportMode.TRAIN_AC),
    FLASHPACKER("Budget hotel", "1–2★ private rooms, cafés, 3AC trains, AC buses & autos", true, 1.6, 1.4, 1.5, 15_000, false, TransportMode.TRAIN_AC),
    COMFORT("3★ comfort", "3-star hotels, restaurants, 2AC trains & cabs", true, 2.2, 2.2, 2.0, 30_000, true, TransportMode.TRAIN_2AC),
    PREMIUM("4★ premium", "4-star hotels, good restaurants, 1AC trains, flights & cabs", true, 3.0, 3.0, 2.5, 60_000, true, TransportMode.TRAIN_1AC);

    companion object {
        fun fromName(name: String?): TravelStyle = entries.firstOrNull { it.name == name } ?: BACKPACKER
    }
}
