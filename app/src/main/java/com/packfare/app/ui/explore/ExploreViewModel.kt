package com.packfare.app.ui.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.data.PlaceCacheEntity
import com.packfare.app.domain.Destination
import com.packfare.app.domain.Destinations
import com.packfare.app.explore.ExploreRepository
import com.packfare.app.explore.PlaceInfo
import com.packfare.app.explore.SearchHit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.packfare.app.domain.BudgetPlanner
import com.packfare.app.domain.BudgetSuggestion
import com.packfare.app.domain.LegKind
import com.packfare.app.domain.PlanStop
import com.packfare.app.domain.TransportMode
import com.packfare.app.domain.TravelStyle
import com.packfare.app.domain.TravelPref
import com.packfare.app.fares.FareSearch
import com.packfare.app.fares.FlightOptions
import com.packfare.app.domain.FareEstimator
import com.packfare.app.domain.RouteOption
import com.packfare.app.util.Prefs
import java.time.LocalDate

class ExploreViewModel(private val repo: ExploreRepository, private val prefs: Prefs? = null) : ViewModel() {

    /** Where fares in the lists are counted from ("From Bengaluru: ✈️ ≈₹5,400 · 🚆 ≈₹900"). */
    var home by mutableStateOf(Destinations.find(prefs?.homeCity.orEmpty()))
        private set

    /** Which stay style the ≈₹/day figures use (dorms up to 4★ hotels). */
    var style by mutableStateOf(TravelStyle.BACKPACKER)

    /** Which fares the "From <home>" line shows. */
    var travelPref by mutableStateOf(TravelPref.fromName(prefs?.travelPref))
        private set

    fun chooseTravelPref(p: TravelPref) {
        travelPref = p
        prefs?.travelPref = p.name
    }

    fun pickHome(d: Destination?) {
        home = d
        prefs?.homeCity = d?.name
    }

    var query by mutableStateOf("")
        private set
    var localHits by mutableStateOf<List<Destination>>(emptyList())
        private set
    var webHits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val recent: StateFlow<List<PlaceCacheEntity>> =
        repo.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    /** Device location (lat, lon) once the user taps "Near me". */
    var myLocation by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var nearby by mutableStateOf<List<Pair<Destination, Int>>>(emptyList())
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    fun onLocation(loc: Pair<Double, Double>?) {
        if (loc == null) {
            locationError = "Couldn't get your location. Check that location is on and allowed for Packfare."
            return
        }
        locationError = null
        myLocation = loc
        nearby = Destinations.nearest(loc.first, loc.second, limit = 6)
    }

    fun onQueryChange(q: String) {
        query = q
        localHits = Destinations.search(q, limit = 15)
        searchJob?.cancel()
        if (q.trim().length < 3) {
            webHits = emptyList()
            error = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // debounce typing, and stay polite to Wikivoyage
            searching = true
            error = null
            webHits = runCatching { repo.search(q.trim()) }
                .onFailure { error = "Offline? Showing places saved on your phone." }
                .getOrDefault(emptyList())
            searching = false
        }
    }
}

class PlaceViewModel(
    private val repo: ExploreRepository,
    private val title: String,
    private val fares: FareSearch? = null,
    private val prefs: Prefs? = null,
) : ViewModel() {

    var place by mutableStateOf<PlaceInfo?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // ---- "Your trip here" calculator ----
    var originText by mutableStateOf(prefs?.homeCity.orEmpty())
    var nights by mutableStateOf(3)
        private set
    var style by mutableStateOf(TravelStyle.BACKPACKER)
    var returnHome by mutableStateOf(true)
    /** Air / train / bus preference (shared with the planner). */
    var travelPref by mutableStateOf(TravelPref.fromName(prefs?.travelPref))
        private set

    fun chooseTravelPref(p: TravelPref) {
        travelPref = p
        prefs?.travelPref = p.name
        modeChoices = emptyMap()
    }

    /** Hotel styles: true = two people share a room (half the room price each), false = room to yourself. */
    var sharingRoom by mutableStateOf(true)
    var travelDate by mutableStateOf(LocalDate.now().plusDays(14))
    private var modeChoices by mutableStateOf<Map<String, TransportMode>>(emptyMap())
    private var liveFlights by mutableStateOf<Map<String, Long>>(emptyMap())
    var checkingFares by mutableStateOf(false)
        private set
    var fareNote by mutableStateOf<String?>(null)
        private set

    val origin: Destination? get() = Destinations.all.firstOrNull { it.name.equals(originText.trim(), ignoreCase = true) }
    val originSuggestions: List<Destination>
        get() = if (origin != null) emptyList() else Destinations.search(originText, limit = 5)

    /** Full per-person budget for going there from [origin]: fares both ways + stay, food, rides, sights. */
    val tripBudget: BudgetSuggestion?
        get() {
            val p = place ?: return null
            val dest = p.asDestination()
            val o = origin?.takeIf { it.name != dest.name }
            val stops = listOf(PlanStop(dest, nights, p.costs))
            val back = if (returnHome) o else null
            val hops = BudgetPlanner.hops(stops, o, back)
            val overrides = hops.mapIndexedNotNull { i, (a, b, _) -> modeChoices[BudgetPlanner.key(a, b)]?.let { i to it } }.toMap()
            return BudgetPlanner.suggest(
                stops, style, origin = o, returnTo = back, modeOverrides = overrides, liveFlights = liveFlights,
                sharingRoom = sharingRoom,
                pref = travelPref,
            )
        }

    // ---- Flights from the origin ----
    var flights by mutableStateOf<FlightOptions?>(null)
        private set
    var loadingFlights by mutableStateOf(false)
        private set
    /** The origin the loaded [flights] are from; they're hidden once the origin changes. */
    var flightsFrom by mutableStateOf<String?>(null)
        private set

    /** Offline estimates, one per sensible arrival airport (e.g. Kullu: KUU and IXC), best first. */
    val flightPlans: List<com.packfare.app.domain.FlightPlan>
        get() {
            val p = place ?: return emptyList()
            val o = origin ?: return emptyList()
            val dest = p.asDestination()
            return if (o.name == dest.name) emptyList() else com.packfare.app.domain.FlightPlanner.choicesByArrivalAirport(o, dest).take(3)
        }

    fun findFlights() {
        val p = place ?: return
        val o = origin ?: return
        val search = fares ?: return
        loadingFlights = true
        viewModelScope.launch {
            flights = runCatching { search.flights(o, p.asDestination(), travelDate) }.getOrNull()
            flightsFrom = o.name
            // Feed the cheapest live fare into the trip budget too.
            // The budget uses the best airport pair, so take that pair's cheapest live fare.
            flights?.plans?.firstOrNull()?.live?.firstOrNull()?.let { q ->
                liveFlights = liveFlights + (BudgetPlanner.key(o, p.asDestination()) to q.airFarePaise)
            }
            loadingFlights = false
        }
    }

    init {
        load(false)
    }

    fun load(force: Boolean) {
        viewModelScope.launch {
            loading = true
            error = null
            val result = runCatching { repo.place(title, forceRefresh = force) }.getOrNull()
            if (result == null) {
                error = "Couldn't load \"$title\". Check your connection."
            } else {
                place = result
            }
            loading = false
        }
    }

    fun pickOrigin(d: Destination) {
        originText = d.name
        prefs?.homeCity = d.name
        liveFlights = emptyMap()
        flights = null
    }

    fun nearMe(loc: Pair<Double, Double>?) {
        loc ?: return
        pickOrigin(Destinations.nearest(loc.first, loc.second, 1).first().first)
    }

    fun changeNights(delta: Int) {
        nights = (nights + delta).coerceIn(1, 60)
    }

    fun chooseMode(from: Destination, to: Destination, mode: TransportMode) {
        modeChoices = modeChoices + (BudgetPlanner.key(from, to) to mode)
    }

    /** Pulls live flight prices for the trip there and back (when a Travelpayouts token is set). */
    fun checkLiveFares() {
        val plan = tripBudget ?: return
        val search = fares ?: return
        if (!search.liveFlights) {
            fareNote = "Live flight prices are off (no Travelpayouts token). Fares shown are estimates."
            return
        }
        checkingFares = true
        viewModelScope.launch {
            val found = mutableMapOf<String, Long>()
            plan.legs.forEach { leg ->
                val date = if (leg.kind == LegKind.RETURN) travelDate.plusDays(nights.toLong()) else travelDate
                val r = runCatching { search.search(leg.from, leg.to, date) }.getOrNull() ?: return@forEach
                r.quotes.filter { it.live && it.mode == TransportMode.FLIGHT }.minByOrNull { it.pricePaise }
                    ?.let { found[BudgetPlanner.key(leg.from, leg.to)] = it.airFarePaise }
            }
            liveFlights = liveFlights + found
            fareNote = if (found.isEmpty()) "No live flight prices for these dates; estimates shown." else "Live flight prices added."
            checkingFares = false
        }
    }
}
