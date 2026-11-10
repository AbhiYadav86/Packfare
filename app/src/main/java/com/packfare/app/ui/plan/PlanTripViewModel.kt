package com.packfare.app.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.data.LegEntity
import com.packfare.app.data.TripEntity
import com.packfare.app.data.TripRepository
import com.packfare.app.domain.ActivityIdea
import com.packfare.app.domain.ActivityPicks
import com.packfare.app.domain.BookingItem
import com.packfare.app.domain.BudgetPlanner
import com.packfare.app.domain.BudgetSuggestion
import com.packfare.app.domain.CostTier
import com.packfare.app.domain.DailyCosts
import com.packfare.app.domain.Destination
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.ItineraryBuilder
import com.packfare.app.domain.ItineraryDay
import com.packfare.app.domain.LegKind
import com.packfare.app.domain.Money
import com.packfare.app.domain.Pace
import com.packfare.app.domain.PlanStop
import com.packfare.app.domain.Theme
import com.packfare.app.domain.TransportMode
import com.packfare.app.domain.TravelStyle
import com.packfare.app.domain.TripSearch
import com.packfare.app.explore.ExploreRepository
import com.packfare.app.fares.FareSearch
import com.packfare.app.util.Prefs
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Search box first, then the results tabs (Itinerary, Getting there, Stays, Budget). */
enum class PlanPhase { SEARCH, RESULTS }

/** A stop being planned. [liveSamples] > 0 means costs were refined with Wikivoyage prices. */
data class StopDraft(
    val destination: Destination,
    val nights: Int,
    val costs: DailyCosts,
    val liveSamples: Int = 0,
)

/**
 * Plan a trip, Polarsteps-style:
 *  1. Where from (home city / near me) and whether you return there.
 *  2. Let Packfare suggest an itinerary (days, themes, regions, pace) or build stops by hand.
 *  3. See the full budget: stays, food, local rides, sights, and every fare (there, between stops,
 *     and back), with the option to switch any leg's mode and pull live flight prices.
 *  4. A day-by-day plan with highlights from the guide, then create the trip.
 */
class PlanTripViewModel(
    private val repo: TripRepository,
    private val explore: ExploreRepository,
    private val fares: FareSearch,
    private val prefs: Prefs,
    initialStops: List<String>,
    initialDays: Int?,
    initialBudgetPaise: Long?,
    initialFrom: String?,
    /** From Explore: -2 = resume the draft, 0..5 = run that recent search, -1 = nothing. */
    resume: Int = -1,
    /** From Explore's suggested trips: style and transport to plan with. */
    initialStyle: TravelStyle? = null,
) : ViewModel() {

    var phase by mutableStateOf(if (initialStops.isNotEmpty() || resume != -1) PlanPhase.RESULTS else PlanPhase.SEARCH)
    var name by mutableStateOf("")
    var myName by mutableStateOf(prefs.travellerName.orEmpty())
    var startDate by mutableStateOf(LocalDate.now().plusDays(7))
    var people by mutableStateOf(prefs.usualTravellers)
        private set

    // ---- Traveller profile (remembered) ----
    var profileName by mutableStateOf(prefs.travellerName.orEmpty())
        private set
    var usualTravellers by mutableStateOf(prefs.usualTravellers)
        private set
    var foreignVisitor by mutableStateOf(prefs.foreignVisitor)
        private set

    fun saveProfile(name: String, travellers: Int, visitor: Boolean) {
        prefs.travellerName = name
        prefs.usualTravellers = travellers
        prefs.foreignVisitor = visitor
        profileName = name.trim()
        usualTravellers = travellers.coerceIn(1, 20)
        foreignVisitor = visitor
        if (myName.isBlank()) myName = profileName
        if (stops.isEmpty()) people = usualTravellers
    }

    // ---- Recent searches & draft (remembered) ----
    var recentSearches by mutableStateOf(prefs.recentSearches)
        private set
    var draft by mutableStateOf(if (initialStops.isEmpty()) prefs.draft else null)
        private set
    private var created = false
    var style by mutableStateOf(TravelStyle.BACKPACKER)
    /** Air / train / bus preference, remembered for next time. */
    var travelPref by mutableStateOf(com.packfare.app.domain.TravelPref.fromName(prefs.travelPref))
        private set

    fun chooseTravelPref(p: com.packfare.app.domain.TravelPref) {
        travelPref = p
        prefs.travelPref = p.name
        modeChoices = emptyMap() // the preference applies to every leg again
    }

    /** Hotel styles only. null = automatic (groups share, solo travellers don't). */
    var sharingRoom by mutableStateOf<Boolean?>(null)
    var stops by mutableStateOf<List<StopDraft>>(emptyList())
        private set
    var stopQuery by mutableStateOf("")

    // ---- Where from ----
    var originText by mutableStateOf(initialFrom ?: prefs.homeCity.orEmpty())
    var returnHome by mutableStateOf(true)
    /** Exact match only, so half-typed names don't change the budget while you type. */
    val origin: Destination? get() = Destinations.all.firstOrNull { it.name.equals(originText.trim(), ignoreCase = true) }

    // ---- Itinerary suggestion inputs ----
    var suggestNights by mutableStateOf(initialDays ?: 7)
    var suggestThemes by mutableStateOf<Set<Theme>>(emptySet())
    var suggestRegions by mutableStateOf<Set<String>>(emptySet())
    var pace by mutableStateOf(Pace.STEADY)
    var suggestMessage by mutableStateOf<String?>(null)
        private set

    // ---- Fares ----
    /** Modes the user picked, keyed "From>To" so they survive reordering. */
    private var modeChoices by mutableStateOf<Map<String, TransportMode>>(emptyMap())
    /** Live cheapest flight per hop, keyed "From>To". */
    private var liveFlights by mutableStateOf<Map<String, Long>>(emptyMap())
    var checkingFlights by mutableStateOf(false)
        private set
    var flightMessage by mutableStateOf<String?>(null)
        private set
    val liveFlightsAvailable: Boolean get() = fares.liveFlights

    // ---- Itinerary text ----
    private var highlights by mutableStateOf<Map<String, List<com.packfare.app.domain.ActivityIdea>>>(emptyMap())

    /** Budget the user typed; null = follow the suggestion. */
    var budgetOverride by mutableStateOf<String?>(initialBudgetPaise?.let { Money.toUpiAmount(it).removeSuffix(".00") })
        private set

    var myLocation by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var locationMessage by mutableStateOf<String?>(null)
        private set

    private var creating = false

    init {
        initialStyle?.let { style = it }
        // Stops may carry their nights ("Manali~3"); otherwise share the days out evenly.
        val parsed = initialStops.map { s ->
            val name = s.substringBefore('~')
            name to s.substringAfter('~', "").toIntOrNull()
        }
        val nightsEach = if (initialDays != null && parsed.isNotEmpty()) (initialDays / parsed.size).coerceAtLeast(1) else 3
        when {
            resume == -2 -> prefs.draft?.let { applySearch(it, resume = true) } ?: run { phase = PlanPhase.SEARCH }
            resume >= 0 -> prefs.recentSearches.getOrNull(resume)?.let { applySearch(it, resume = false) } ?: run { phase = PlanPhase.SEARCH }
            else -> viewModelScope.launch {
                parsed.forEach { (n, nights) -> addStopByName(n, nights ?: nightsEach) }
                if (stops.isNotEmpty()) suggestNights = stops.sumOf { it.nights }
                if (name.isBlank() && stops.isNotEmpty()) name = defaultName()
                if (stops.isNotEmpty()) showResults() else loadHighlights(allowNetwork = false)
            }
        }
    }

    // ---------------- Search ----------------

    /** The search box as it stands, for recent searches and drafts. */
    fun currentSearch(): TripSearch = TripSearch(
        from = origin?.name,
        stops = stops.map { it.destination.name to it.nights },
        startDate = startDate,
        nights = if (stops.isEmpty()) suggestNights else stops.sumOf { it.nights },
        people = people,
        style = style,
        pref = travelPref,
        returnHome = returnHome,
        themes = suggestThemes,
        pace = pace,
        name = name,
    )

    fun changeTripNights(delta: Int) {
        suggestNights = (suggestNights + delta).coerceIn(1, 60)
    }

    /**
     * "Search trips": with destinations picked, share the nights across them; with none, let Packfare
     * suggest a route. Either way, show the results and remember the search.
     */
    fun search() {
        suggestMessage = null
        if (stops.isEmpty()) {
            suggestItinerary(fromSearch = true)
            return
        }
        val split = TripSearch.splitNights(suggestNights, stops.size)
        stops = stops.mapIndexed { i, s -> s.copy(nights = split[i]) }
        if (name.isBlank()) name = defaultName()
        showResults()
    }

    private fun showResults() {
        phase = PlanPhase.RESULTS
        val s = currentSearch()
        recentSearches = TripSearch.remember(recentSearches, s)
        prefs.recentSearches = recentSearches
        prefs.draft = s
        loadHighlights(allowNetwork = true)
    }

    fun editSearch() {
        phase = PlanPhase.SEARCH
    }

    /** Fill the search box from a recent search or the draft, then show results. */
    fun applySearch(s: TripSearch, resume: Boolean) {
        originText = s.from.orEmpty()
        startDate = if (s.startDate.isBefore(LocalDate.now())) LocalDate.now().plusDays(7) else s.startDate
        suggestNights = s.nights.coerceAtLeast(1)
        people = s.people
        style = s.style
        travelPref = s.pref
        returnHome = s.returnHome
        suggestThemes = s.themes
        pace = s.pace
        name = s.name
        modeChoices = emptyMap()
        liveFlights = emptyMap()
        stops = emptyList()
        viewModelScope.launch {
            s.stops.forEach { (n, nights) -> addStopByName(n, nights) }
            when {
                stops.isEmpty() -> suggestItinerary(fromSearch = true)
                resume -> { phase = PlanPhase.RESULTS; loadHighlights(allowNetwork = true) }
                else -> showResults()
            }
        }
        if (resume) draft = null
    }

    fun discardDraft() {
        draft = null
        prefs.draft = null
    }

    fun removeRecent(s: TripSearch) {
        recentSearches = recentSearches.filterNot { it == s }
        prefs.recentSearches = recentSearches
    }

    /** Keep an unfinished plan so it can be resumed from the search screen. */
    override fun onCleared() {
        if (!created && stops.isNotEmpty()) prefs.draft = currentSearch()
        super.onCleared()
    }

    // ---------------- Derived ----------------

    val stopSuggestions: List<Destination>
        get() = Destinations.search(stopQuery, limit = 6).filter { s -> stops.none { it.destination.name == s.name } }

    val originSuggestions: List<Destination>
        get() = if (origin != null) emptyList() else Destinations.search(originText, limit = 5)

    private val planStops: List<PlanStop> get() = stops.map { PlanStop(it.destination, it.nights, it.costs) }

    /** The estimate before any ticked activities are added on top. */
    private val baseSuggestion: BudgetSuggestion?
        get() {
            if (stops.isEmpty()) return null
            val o = origin
            val back = if (returnHome) o else null
            val hops = BudgetPlanner.hops(planStops, o, back)
            val overrides = hops.mapIndexedNotNull { i, (a, b, _) ->
                modeChoices[BudgetPlanner.key(a, b)]?.let { i to it }
            }.toMap()
            return BudgetPlanner.suggest(
                planStops, style, people,
                origin = o, returnTo = back, modeOverrides = overrides, liveFlights = liveFlights,
                sharingRoom = sharingRoom,
                pref = travelPref,
            )
        }

    val itinerary: List<ItineraryDay>
        get() = baseSuggestion?.let { ItineraryBuilder.days(it, startDate, highlights) }.orEmpty()

    /** The estimate shown everywhere: base plus whatever ticked activities cost beyond the allowance. */
    val suggestion: BudgetSuggestion?
        get() {
            val base = baseSuggestion ?: return null
            val extra = ActivityPicks.extraPaise(itinerary, skippedIdeas, base.activitiesPaise)
            return if (extra > 0) base.copy(extrasPaise = extra) else base
        }

    // ---- Ticked activities (all ticked by default) ----
    var skippedIdeas by mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleIdea(day: Int, idea: ActivityIdea) {
        val k = ActivityPicks.key(day, idea)
        skippedIdeas = if (k in skippedIdeas) skippedIdeas - k else skippedIdeas + k
    }

    // ---- Booking ticks, remembered on the phone until the trip is created ----
    private var tickRev by mutableIntStateOf(0)

    private val planSig: String
        get() = stops.joinToString(";") { "${it.destination.name}~${it.nights}" } + "@" + startDate

    val ticked: Set<String>
        get() {
            tickRev // read so Compose updates when it changes
            val lines = prefs.bookedPlan.lines()
            return if (lines.firstOrNull() == planSig) lines.getOrNull(1).orEmpty().split("|").filter { it.isNotBlank() }.toSet() else emptySet()
        }

    fun toggleBooked(key: String) {
        val now = ticked.toMutableSet()
        if (!now.add(key)) now.remove(key)
        prefs.bookedPlan = planSig + "\n" + now.joinToString("|")
        tickRev++
    }

    /** The journeys and stays to book, with their ticks. Same keys on the Book tab and in the saved trip. */
    fun bookingItems(): List<BookingItem> {
        val plan = suggestion ?: return emptyList()
        val done = ticked
        val legs = plan.legs.mapIndexed { i, l ->
            BookingItem("leg$i", "${l.from.name} → ${l.to.name} · ${l.option.mode.label}", "leg$i" in done)
        }
        val stays = stops.mapIndexed { i, st ->
            BookingItem("stay$i", "Stay in ${st.destination.name} · ${st.nights} night${if (st.nights == 1) "" else "s"}", "stay$i" in done)
        }
        return legs + stays
    }

    val budgetPaise: Long
        get() = budgetOverride?.let { Money.parse(it) } ?: suggestion?.groupTotalPaise?.let { roundUpTo100(it) } ?: 0L

    val budgetText: String
        get() = budgetOverride ?: suggestion?.groupTotalPaise?.let { Money.toUpiAmount(roundUpTo100(it)).removeSuffix(".00") }.orEmpty()

    val canCreate: Boolean get() = stops.isNotEmpty() && !creating

    // ---------------- Where from ----------------

    fun pickOrigin(d: Destination) {
        originText = d.name
        prefs.homeCity = d.name
    }

    fun startNearMe(loc: Pair<Double, Double>?) {
        if (loc == null) {
            locationMessage = "Couldn't get your location."
            return
        }
        myLocation = loc
        val (nearest, km) = Destinations.nearest(loc.first, loc.second, limit = 1).first()
        locationMessage = "Starting from ${nearest.name} (~$km km from you)."
        pickOrigin(nearest)
    }

    // ---------------- Itinerary suggestion ----------------

    fun toggleTheme(t: Theme) {
        suggestThemes = if (t in suggestThemes) suggestThemes - t else suggestThemes + t
    }

    fun toggleRegion(r: String) {
        suggestRegions = if (r in suggestRegions) suggestRegions - r else suggestRegions + r
    }

    fun changeSuggestNights(delta: Int) {
        suggestNights = (suggestNights + delta).coerceIn(2, 60)
    }

    /** Replaces the stops with a suggested route. Needs a starting point. */
    fun suggestItinerary(fromSearch: Boolean = false) {
        val start = origin ?: suggestRegions.firstOrNull()?.let { Destinations.hubsIn(it).firstOrNull() }
        if (start == null) {
            suggestMessage = "Tell Packfare where you're starting from (or pick a region) first."
            return
        }
        val picks = ItineraryBuilder.suggestStops(start, suggestNights, suggestThemes, suggestRegions, pace, style)
        if (picks.isEmpty()) {
            suggestMessage = "No good matches. Try fewer themes or another region."
            return
        }
        viewModelScope.launch {
            stops = picks.map { p ->
                val live = runCatching { explore.cachedCosts(p.destination) }.getOrNull()
                StopDraft(p.destination, p.nights, live?.first ?: p.destination.baseline, live?.second ?: 0)
            }
            modeChoices = emptyMap()
            suggestMessage = "Suggested ${picks.size} stops for ${suggestNights} nights. Tweak anything below."
            name = defaultName()
            if (fromSearch) showResults() else loadHighlights(allowNetwork = true)
        }
    }

    /** Fills the day-by-day plan with "See"/"Do" ideas from Wikivoyage (cached first, then online). */
    fun loadHighlights(allowNetwork: Boolean) {
        val current = stops.map { it.destination }
        viewModelScope.launch {
            val found = current.filter { it.region != "Other" }.associate { d ->
                d.name to runCatching { explore.activities(d, allowNetwork) }.getOrDefault(emptyList())
            }
            highlights = highlights + found.filterValues { it.isNotEmpty() }
        }
    }

    // ---------------- Fares ----------------

    fun chooseMode(from: Destination, to: Destination, mode: TransportMode) {
        modeChoices = modeChoices + (BudgetPlanner.key(from, to) to mode)
    }

    /** Asks the internet for real flight prices on every hop with airports at both ends. */
    fun checkLiveFlights() {
        val plan = suggestion ?: return
        if (!fares.liveFlights) {
            flightMessage = "Live flight prices are off (no Travelpayouts token). Showing estimates."
            return
        }
        checkingFlights = true
        flightMessage = null
        viewModelScope.launch {
            val dates = legDates(plan)
            val found = mutableMapOf<String, Long>()
            plan.legs.forEachIndexed { i, leg ->
                if (com.packfare.app.domain.FlightPlanner.plans(leg.from, leg.to).isEmpty()) return@forEachIndexed
                val results = runCatching { fares.search(leg.from, leg.to, dates[i]) }.getOrNull() ?: return@forEachIndexed
                results.quotes.filter { it.live && it.mode == TransportMode.FLIGHT }.minByOrNull { it.pricePaise }
                    ?.let { found[BudgetPlanner.key(leg.from, leg.to)] = it.airFarePaise }
            }
            liveFlights = liveFlights + found
            checkingFlights = false
            flightMessage = if (found.isEmpty()) "No live flight prices found for these dates." else
                "Live flight prices found for ${found.size} leg${if (found.size == 1) "" else "s"}."
        }
    }

    /** Travel date of each leg: outbound on day 1, then after each stop's nights. */
    fun legDates(plan: BudgetSuggestion): List<LocalDate> {
        var offset = 0L
        val nightsByStop = plan.stops.map { it.stop.destination.name to it.stop.nights.toLong() }
        return plan.legs.map { leg ->
            when (leg.kind) {
                LegKind.OUTBOUND -> startDate
                else -> {
                    // Depart after the nights at the stop you're leaving.
                    val leaving = nightsByStop.firstOrNull { it.first == leg.from.name }?.second ?: 0
                    offset += leaving
                    startDate.plusDays(offset)
                }
            }
        }
    }

    // ---------------- Stops ----------------

    fun onBudgetChange(text: String) {
        budgetOverride = text
    }

    fun resetBudget() {
        budgetOverride = null
    }

    fun changePeople(delta: Int) {
        people = (people + delta).coerceIn(1, 20)
    }

    fun addStop(d: Destination) {
        stopQuery = ""
        viewModelScope.launch {
            addDestination(d, 2)
            loadHighlights(allowNetwork = false)
        }
    }

    /** Adds a typed place that isn't in the bundled list (no fare estimate for its legs). */
    fun addCustomStop() {
        val text = stopQuery.trim()
        if (text.isEmpty()) return
        stopQuery = ""
        viewModelScope.launch { addStopByName(text, 2) }
    }

    /** Tapping a hub on the map adds it (or removes it if it's already a stop). */
    fun toggleStopFromMap(wikivoyageTitle: String) {
        val d = Destinations.all.firstOrNull { it.wikivoyage == wikivoyageTitle } ?: return
        val idx = stops.indexOfFirst { it.destination.name == d.name }
        if (idx >= 0) removeStop(idx) else addStop(d)
    }

    fun changeNights(index: Int, delta: Int) {
        stops = stops.mapIndexed { i, s -> if (i == index) s.copy(nights = (s.nights + delta).coerceIn(1, 60)) else s }
    }

    fun removeStop(index: Int) {
        stops = stops.filterIndexed { i, _ -> i != index }
    }

    fun moveUp(index: Int) {
        if (index <= 0) return
        val list = stops.toMutableList()
        val s = list.removeAt(index)
        list.add(index - 1, s)
        stops = list
    }

    private suspend fun addStopByName(name: String, nights: Int) {
        val local = Destinations.find(name)
        if (local != null) {
            addDestination(local, nights)
            return
        }
        // Not in our table: use what Explore knows (coordinates + prices), if anything.
        val place = runCatching { explore.place(name) }.getOrNull()
        val dest = place?.asDestination() ?: Destination(name, "Other", Double.NaN, Double.NaN, CostTier.MID)
        val costs = place?.costs ?: dest.baseline
        stops = stops + StopDraft(dest, nights, costs, place?.stats?.sampleCount ?: 0)
        place?.activities?.takeIf { it.isNotEmpty() }?.let { highlights = highlights + (dest.name to it) }
    }

    private suspend fun addDestination(d: Destination, nights: Int) {
        val live = runCatching { explore.cachedCosts(d) }.getOrNull()
        stops = stops + StopDraft(d, nights, live?.first ?: d.baseline, live?.second ?: 0)
        if (name.isBlank()) name = defaultName()
    }

    private fun defaultName(): String {
        val first = stops.firstOrNull()?.destination ?: return ""
        val regions = stops.map { it.destination.region }.distinct()
        return if (regions.size == 1 && regions[0] != "Other" && stops.size > 1) "${regions[0]} trip" else "${first.name} trip"
    }

    // ---------------- Create ----------------

    fun create(onCreated: (Long) -> Unit) {
        if (!canCreate) return
        val plan = suggestion ?: return
        creating = true
        val start = startDate
        val dates = legDates(plan)
        viewModelScope.launch {
            val legs = plan.legs.mapIndexed { i, est ->
                LegEntity(
                    tripId = 0,
                    position = i,
                    fromName = est.from.name,
                    toName = est.to.name,
                    mode = est.option.mode.name,
                    farePaise = est.option.farePaise,
                    hours = est.option.hours,
                    dateEpochDay = dates[i].toEpochDay(),
                    overnight = est.option.overnight,
                    nightsAtDestination = if (est.kind == LegKind.RETURN) 0 else
                        stops.firstOrNull { it.destination.name == est.to.name }?.nights ?: 0,
                    notes = when (est.kind) {
                        LegKind.OUTBOUND -> "Getting there"
                        LegKind.RETURN -> "Heading home"
                        LegKind.BETWEEN -> null
                    },
                )
            }
            val trip = TripEntity(
                name = name.ifBlank { defaultName().ifBlank { "My trip" } },
                destination = stops.joinToString(" → ") { it.destination.name },
                budgetPaise = budgetPaise,
                startEpochDay = start.toEpochDay(),
                plannedDays = plan.days,
                style = style.name,
                bookings = bookingItems().takeIf { it.isNotEmpty() }?.let { BookingItem.encode(it) },
            )
            val id = repo.createPlannedTrip(trip, myName, legs)
            created = true
            prefs.draft = null
            prefs.bookedPlan = ""
            if (prefs.travellerName.isNullOrBlank() && myName.isNotBlank()) prefs.travellerName = myName
            onCreated(id)
        }
    }

    private fun roundUpTo100(paise: Long): Long = ((paise + 10_000 - 1) / 10_000) * 10_000 // to next ₹100
}
