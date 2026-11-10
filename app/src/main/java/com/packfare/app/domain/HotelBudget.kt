package com.packfare.app.domain

/** What a night's room can cost before it breaks the plan, and whether a listing fits. */
object HotelBudget {
    /**
     * Per room (or per bed for dorm styles) per night, from what the plan set aside for this stay.
     * [stayPlannedPaise] is for everyone together; [units] is the number of rooms (or beds) booked.
     */
    fun nightlyCapPaise(stayPlannedPaise: Long, nights: Int, units: Int): Long =
        stayPlannedPaise / (nights.coerceAtLeast(1) * units.coerceAtLeast(1))

    /** Rooms (or beds) needed: dorm beds are one per person, rooms are shared in pairs unless you want your own. */
    fun units(people: Int, rooms: Boolean, sharing: Boolean): Int =
        if (!rooms) people.coerceAtLeast(1) else if (sharing) (people.coerceAtLeast(1) + 1) / 2 else people.coerceAtLeast(1)

    fun fits(pricePaise: Long, capPaise: Long): Boolean = pricePaise <= capPaise
}
