# Packfare: Backpack India on a budget

An offline-first Android app for backpackers in India (Indian and international). It plans a trip with a
suggested budget, tracks daily spending and cash, maps the route with fare estimates, splits costs
with travel buddies, scans receipts and keeps travel documents in an encrypted vault.

## Run it

1. Open this folder in Android Studio (File > Open > Packfare).
2. Let Gradle sync. Accept any offer to install SDK 35 or upgrade the Android Gradle Plugin.
3. Pick an emulator or phone and press Run.

Unit tests (split maths, receipt reading, budget planner, fares, runway, cash, Wikivoyage parsing):
right-click `app/src/test` > Run Tests, or `./gradlew test`.

## What's new in v0.10

- **Plan a trip starts in Explore.** The "Plan a trip" button on the Trips tab opens Explore, now the
  planning hub:
  - Search any place from the header, or pick **what you're into** (🏔️ Mountains, 🏖️ Beaches, 🏰 Heritage,
    🛕 Spiritual, 🌿 Nature, 🍜 Food & city) and a **trip length**.
  - **Suggested trips from your home city**: one ready-made route per theme with stops, nights and an
    all-in price per person (fares there and back, stays in your style, food, sights). "Plan this" opens it
    in the planner's results tabs.
  - **Places for your themes**, closest first, plus "Continue planning" and your recent searches.
  - **Build your own trip** opens the full search page for hand-picked stops.
  - From any place page, "Plan a trip here" continues the same way.
- **More train classes: 3AC, 2AC and 1AC** (still nothing below 3AC). Each travel style books a class by
  default: Backpacker and Budget hotel 3AC, 3★ comfort 2AC, 4★ premium 1AC. Every leg shows all classes
  as options with their prices.
- **New look:** forest green, lake blue and terracotta replace saffron, in light and dark mode, on hero
  headers, maps and the app icon.

## What's new in v0.9

- **Plan a trip, travel-website style.**
  - **Search first:** a big search card with From (or 📍 near me, return home on/off), To (one or more
    destinations, or leave it empty and pick themes and pace for a suggested route), Dates (start + nights)
    and Travellers & style (group size, dorm to 4★, room sharing, air / train / bus). Tap **Search trips**.
  - **Results in tabs**, with the total per person (and for the group) always visible at the bottom next to
    **Create trip**:
    - *Itinerary*: map, stops (nights, reorder, add/remove), day-by-day plan, "suggest a different route".
    - *Getting there*: a card per journey with date, chosen mode, price, the other options as one-tap chips,
      and buttons to check and book: Aviasales, Google Flights, Skyscanner, ixigo, MakeMyTrip for flights
      (per airport, e.g. Kullu–Bhuntar and Chandigarh); IRCTC / redBus / 12Go for trains and buses.
      Live flight prices on demand.
    - *Stays*: each stop's check-in/out dates, price per night at every comfort level (tap to switch),
      your stay cost, and Booking.com / Google Hotels links with dates, guests and rooms filled in.
    - *Budget*: the full breakdown, your budget, trip and traveller names.
- **Remembered for next time** (on this phone only):
  - **Traveller profile:** your name (greeting and trip owner), usual group size (pre-fills searches), and
    "I'm visiting India" (puts 12Go first for trains and buses: PDF tickets, no IRCTC account or Indian phone).
  - **Recent searches:** the last 6, one tap to run again.
  - **Draft:** leave mid-plan and "Continue planning" picks up where you stopped.

## What's new in v0.8

- **About 1,000 searchable places** (was 175): the 175 hand-tuned hubs plus ~550 sights and small
  hill, beach, temple and heritage towns (Kullu, Chitkul, Chopta, Turtuk, Gurez, Gokarna's neighbours,
  Agonda, Nalanda, Ziro's neighbours…) and every town over ~80,000 people, so you can start a trip
  from anywhere. Explore lists hubs first, then "More towns & sights" per region. Places with no
  Wikivoyage article still open, with costs, map and fares from Packfare's own data.
- **Better flight estimates.** For each place Packfare now compares its own or nearest airport with the
  nearest big airport, e.g. Kullu: Kullu–Bhuntar (KUU, 10 km, few pricey flights) *or* Chandigarh (IXC,
  ~220 km by bus). Each option shows air fare + transfers + total time.
  - Routes without direct flights connect via a metro (Bengaluru → Delhi → Kullu–Bhuntar, 1 stop).
  - Small regional airports are priced ~80% higher; busy metro routes slightly lower.
  - 110 airports (was 80).
- **Working flight links.** Links always use airport codes (a town without an airport gave Google
  Flights an empty page) and every option offers Google Flights, Skyscanner, ixigo and MakeMyTrip.
  No booking happens in the app; it just opens the site with the route and date filled in.

### Data sources

- Town coordinates and populations: [GeoNames](https://www.geonames.org) (CC BY 4.0), plus hand-checked
  coordinates for small sights.
- Railway nearby (station within ~12 km): [DataMeet Indian Railways stations](https://github.com/datameet/railways) (ODbL).
- Costs, tiers, flags and permits: Packfare estimates.

## What's new in v0.7

- **Air, train or bus?** Plan a trip, each place's trip calculator, Explore and the Fares tab ask
  "How would you like to travel?" (⭐ Best value / ✈️ Air / 🚆 Train / 🚌 Bus). The choice is remembered and
  used for every leg that has that mode; legs that don't (no railway to Manali, a bus too long for
  Kolkata→Delhi) fall back to the best alternative. Choosing Air adds flights on short hops too when both
  ends have an airport. You can still switch any single leg by tapping it.
- **Nothing below 3AC.** Sleeper class and ordinary state buses are no longer suggested or budgeted;
  options are 3AC trains, AC buses, shared jeeps in the hills, flights and island ferries. Older trips that
  used sleeper or state-bus legs still open fine.

## What's new in v0.6

- **Flights for every destination.**
  - Explore: set your home city once (🏠 chip). Every place in the lists shows
    "From Bengaluru: ✈️ ≈₹5,400 · 4h  🚆 ≈₹900 · 20h".
  - Place screen: new **✈️ Flights from <your city>** card with a door-to-door estimate, "Find live fares"
    (up to 6 live fares with airline, time, stops and booking link) and a Google Flights button. The cheapest
    live fare goes straight into the trip budget.
  - Flight estimates now include getting to and from the airport (e.g. Kasol → Kullu-Bhuntar is 30 km by
    cab/bus), so hill towns and small places are priced honestly. Short hops (under 250 km) don't offer flights.
  - About 80 airports, including regional UDAN ones (Kullu, Dharamshala, Jaisalmer, Diu, Agatti…).
- **175 places** (was 94), now grouped by state: Ahmedabad, Vadodara, Statue of Unity, Dwarka, Somnath, Gir,
  Diu, Rann of Kutch, Pune, Lonavala, Mahabaleshwar, Aurangabad (Ajanta & Ellora), Bhopal, Indore, Ujjain,
  Mandu, Kanha, Bandhavgarh, Srinagar, Gulmarg, Pahalgam, Chandigarh, Lucknow, Ayodhya, Haridwar, Nainital,
  Jim Corbett, Ranthambore, Bundi, South Goa, Kumarakom, Coimbatore, Warangal, Digha, Agatti and many more.
  Cities aren't only for backpackers any more.
- **Hotels up to 4★.** Travel styles are now Backpacker (dorms), Budget hotel (1–2★), 3★ comfort and
  4★ premium. Each place shows room prices per night; Wikivoyage "Mid-range" and "Splurge" listings refine
  the 3★/4★ prices when available. "Sharing a room" splits the room price between two people; solo
  travellers pay the full room. Higher styles also prefer AC trains, cabs and flights when picking the best route.

## What's new in v0.5

- **Travel fares are part of every budget.**
  - Plan a trip asks where you're starting from (home city, or 📍 near me) and whether you return there.
    The budget now splits **Getting around** (there & back, between stops, local rides) from **On the ground**
    (stay, food, sights, buffer).
  - Tap any leg to switch mode (sleeper train, AC bus, flight…); the budget updates instantly.
  - "✈️ Check live flight prices" swaps estimated flight fares for real ones (Travelpayouts token needed).
  - Explore → any place → **Your trip here, all in**: from your city, N nights, style, fares there and back
    (switchable), stay, food, rides, sights, total per person.
  - Trip > Today shows planned fares vs what you've spent on travel.
- **Itinerary builder.** "✨ Suggest my route": nights, themes (mountains, beaches, heritage, spiritual,
  nature, food & cities), pace (relaxed 3 nights/stop, steady 2, fast 1) and optional regions. Packfare picks
  stops near your start, orders them to keep hops short, shares out the nights, and budgets it all.
- **Day-by-day plan** with travel days, dates and "See/Do" ideas from Wikivoyage for each stop.
- Creating the trip saves every leg (including getting there and back) to the Route tab with dates.

## What's new in v0.4

- **Maps + location** (MapLibre with free OpenStreetMap tiles from OpenFreeMap, no API key):
  - Explore: every hub on a map (🟢 cheap, 🟠 mid, 🔴 pricey), tap a dot to open it; "📍 Near me" shows your
    position and the closest hubs.
  - Place screen: map of the place and nearby hubs.
  - Plan a trip: numbered stops joined by a route line; tap grey dots to add/remove stops; "📍 Start near me".
  - Trip > Route: map of the whole route.
  - Location uses Android's own LocationManager (no Google Play services needed). Coarse location is enough.
- **94 backpacker hubs** (was 45), especially South and East: Ooty, Coonoor, Kodaikanal, Wayanad, Thekkady,
  Kovalam, Kanyakumari, Rameswaram, Mahabalipuram, Hyderabad, Araku, Gandikota, Puri, Konark, Chilika,
  Sundarbans, Kalimpong, Pelling, Bodh Gaya, Kaziranga, Majuli, Cherrapunji, Dawki, Kohima, Ziro, Neil Island…
  Each now has its nearest airport.
- **Travel styles:** Backpacker and Flashpacker (Shoestring removed).
- **Fares tab: cheapest way there.** From/To/date →
  - ✈️ **Live flight prices** from the Travelpayouts (Aviasales) Data API between the nearest airports, with
    booking links (affiliate commission if you add your marker).
  - 🚆🚌🚙⛴️ Train, bus, jeep and ferry **estimates**, each linking to IRCTC / redBus / Google to check live seats.
  - Picks the **cheapest**, **best value** (money + time) and **fastest**.
  - Also reachable from a place ("Find the cheapest way to get here") and from each route leg.

### Turning on live flight prices

1. Sign up (free) at travelpayouts.com and join the Aviasales programme.
2. Copy your API token (Profile → API token) and your partner ID (marker).
3. Add to `local.properties` in the project root (this file is never committed):
   ```
   travelpayouts.token=YOUR_TOKEN
   travelpayouts.marker=YOUR_MARKER
   ```
4. Rebuild. The Fares tab will say "Live flight prices: on".

Notes: these are real fares travellers found in the last few days (cached up to 7 days), not a live seat
check, so the booking page may differ slightly. The token ships inside the app; it's a read-only data
token, but if you want to hide it later, put a tiny Cloudflare Worker in front of the API.

**Why trains and buses are estimates:** IRCTC and the big bus platforms don't offer a free, official public
API. Unofficial scrapers exist but break often and may violate those sites' terms, so Packfare doesn't use
them. `FareSearch` has a `FareProvider` plug-in point for a licensed API (an IRCTC partner, a bus
aggregator, or 12Go via Travelpayouts) when you're ready.

From v0.3: daily budget runway, "How long can I travel?", cash wallet with ATM warnings, route legs with
overnight savings, encrypted document vault, Explore with Wikivoyage prices, and the Plan-a-trip budget wizard.

Still there from earlier versions: receipt scanning (ML Kit, on-device), equal/exact/share splits,
settle up with UPI links, editing, WhatsApp summary.

## How the numbers work

- **Cost table** (`domain/Destinations.kt` hubs + `domain/ExtraDestinations.kt`): ~1,000 places; the 175 hubs have each with a cost tier, coordinates, nearest airport,
  hills/rail/island flags, few-ATM flags and permit notes. Tier baselines are 2026 estimates for a
  backpacker (dorm, dhaba food, local buses, a few sights). Hotel styles use per-room prices by tier
  (budget ₹1,200–2,800, 3★ ₹2,800–6,000, 4★ ₹5,000–11,000 a night) and scale food, rides and sights.
- **Airports** (`domain/Airports.kt`): coordinates for town-to-airport transfer estimates.
- **Live prices** (`explore/WikivoyageParser.kt`): reads the `price=` field of Wikivoyage `{{sleep}}` and
  `{{eat}}` listings, keeps rupee prices, and uses the medians when there are at least 3 samples.
- **Fares** (`domain/FareEstimator.kt`): straight-line distance × road/rail factors × typical per-km
  fares per mode, with slower speeds on mountain roads. Flights: typical fare by air distance (regional
  airports cost more) plus cab/bus to and from each airport. Always shown as "≈". Not live prices.
- **Planner** (`domain/BudgetPlanner.kt`): stays + food + local + activities + intercity + 8% buffer −
  overnight savings.

All of this runs on the phone. The only network use is Explore talking to the public Wikivoyage API.

## Before publishing

- Set a real contact in `WikivoyageClient.DEFAULT_USER_AGENT` (Wikimedia's API policy asks for one).
- Keep the CC BY-SA attribution on the place screen (Wikivoyage licence).
- Review the cost table numbers for your target season and update them yearly.

## Code map

```
app/src/main/java/com/packfare/app/
  domain/        Pure Kotlin: Money, SplitCalculator, Destinations (cost table), TravelStyle,
                 FareEstimator, BudgetPlanner (incl. home legs), Itinerary (themes, route builder,
                 day-by-day), Runway + CashWallet, PaymentMethod, Category
  explore/       WikivoyageParser (pure), WikivoyageClient (HTTP), ExploreRepository (cache)
  vault/         Vault: Keystore-backed AES-GCM file encryption
  receipt/       ReceiptParser, ReceiptScanner (ML Kit), ReceiptStorage
  data/          Room entities (trips, members, legs, expenses, shares, cash, documents, place cache), DAOs
  fares/         FareModels (pure: picker, booking links), TravelpayoutsFlights, FareSearch
  ui/home/       Bottom navigation: Trips, Explore, Fares, How long?, Vault
  ui/fares/      Fare search screen
  ui/components/ PackfareMap (MapLibre wrapper), donut chart, avatars, budget bar
  ui/plan/       Plan-a-trip wizard, reverse planner
  ui/explore/    Search and place screens
  ui/trip/       Today, Expenses, Route, Settle up, People tabs + dialogs
  ui/expense/    Add/edit expense (receipt scan, split modes, cash/UPI/card, route leg)
  ui/vault/      Vault list and document viewer
```

## Roadmap

- Group sync between phones (Nearby Connections, offline) and QR trip sharing
- Offline map packs (PMTiles per region) for no-signal areas
- Licensed live train/bus fares via the FareProvider plug-in
- Remaining backpacker features: split with strangers (4), fair-price guide (7), checklists (9),
  shareable trip card (10), states collected (11), safety basics (12)
- Home-currency display for international travellers
