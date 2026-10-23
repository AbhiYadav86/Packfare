package com.packfare.app.util

import android.content.Context
import com.packfare.app.domain.TripSearch

/** Small user settings that aren't worth a database table. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("packfare", Context.MODE_PRIVATE)

    /** The hub the user usually starts trips from (e.g. "Bengaluru"), used for fares there and back. */
    var homeCity: String?
        get() = sp.getString("home_city", null)
        set(value) = sp.edit().putString("home_city", value).apply()

    /** Air, train, bus or "best value" (a TravelPref name), asked once and remembered. */
    var travelPref: String?
        get() = sp.getString("travel_pref", null)
        set(value) = sp.edit().putString("travel_pref", value).apply()

    // ---- Traveller profile ----

    /** First name, used to greet the user and as their name in new trips. */
    var travellerName: String?
        get() = sp.getString("traveller_name", null)
        set(value) = sp.edit().putString("traveller_name", value?.trim()?.ifBlank { null }).apply()

    /** How many people usually travel together (pre-fills new searches). */
    var usualTravellers: Int
        get() = sp.getInt("usual_travellers", 1)
        set(value) = sp.edit().putInt("usual_travellers", value.coerceIn(1, 20)).apply()

    /** Foreign visitor: 12Go links for trains and buses (no IRCTC account or Indian phone needed). */
    var foreignVisitor: Boolean
        get() = sp.getBoolean("foreign_visitor", false)
        set(value) = sp.edit().putBoolean("foreign_visitor", value).apply()

    // ---- Recent searches & draft ----

    var recentSearches: List<TripSearch>
        get() = TripSearch.decodeList(sp.getString("recent_searches", null))
        set(value) = sp.edit().putString("recent_searches", TripSearch.encodeList(value)).apply()

    /** The unfinished plan, resumed from the search screen. Null once the trip is created. */
    var draft: TripSearch?
        get() = sp.getString("draft", null)?.let { TripSearch.decode(it) }
        set(value) = sp.edit().putString("draft", value?.let { TripSearch.encode(it) }).apply()
}
