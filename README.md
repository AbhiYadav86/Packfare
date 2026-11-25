# Packfare: Backpack India on a budget

An offline-first Android app for backpackers in India (Indian and international). It plans a trip with a
suggested budget, tracks daily spending and cash, maps the route with fare estimates, splits costs
with travel buddies, scans receipts and keeps travel documents in an encrypted vault.

## v0.15 – Plan, activities and bookings inside every trip

- No more "price varies": things to do without a guide price get an estimate from the kind of place, marked "est.".
- Place page flights: the usual price range for the flight (our estimate) next to Google Flights, Skyscanner, ixigo and MakeMyTrip. Those sites have no free public price feed, so real prices still come from "Find live fares".
- Trip > Today: a day-by-day plan with suggested things to do (photos when online), + Add / Added, your own activities with a done tick, "Add activity" button.
- Trip > Bookings (new tab): book links per journey and stay, "I've booked this" with the price paid, ticket/confirmation PDFs or photos stored encrypted with the trip. First time a planned trip opens, it asks if anything is already booked.
- Budget after bookings: trip budget minus booked prices, minus planned prices still to book, minus bills logged.
- Database version 4 (migration 3 to 4).

## v0.14.1 – Fixes from testing

- Place page: one "Live flight prices" button instead of two (the flights card keeps "Find live fares").
- The stay level you pick on a place page (e.g. 3★ comfort) now carries into the planner.
- Back from the planner goes to the place (or Home) you came from, not to the search form. Tap the title to edit the search.
- Discover map: Cheap / Mid / Pricey chips filter the dots; pins load straight away; Near me shows "Finding you…" and then the closest place.

## v0.14 – Saved booking ticks and tickable activities

- Booking ticks on the Book tab are remembered, and the checklist is saved with the trip (Trips > open trip > Today shows "Bookings" with tickboxes). Database version 3 with a migration.
- Itinerary activities each have a tickbox. Ticked activities only raise the trip total when together they cost more than the daily activities allowance already included; the breakdown shows "Extra activities you ticked".
- Events, bill scanning and the reimbursement PDF are unchanged.

## v0.13 – One trip spine, four tabs, a friendlier compass

- **Four tabs:** Home, Discover, Trips, Wallet. Home has "Where to next?", your unfinished plan, and quick starts (Scan a bill, New event, Compare fares, How long?). Wallet holds Bills (scan, events, reimbursement) and Documents (the vault).
- **Trip builder tabs:** Setup (where you'll stay first, with price differences between comfort levels), Itinerary, Travel, Book. A single Trip total bar stays on screen; tap it for the full breakdown and your budget cap. The old Budget tab is gone.
- **Book** is a checklist of journeys and stays with one set of links each and a "booked" tick.
- **Compass on every screen:** the page colour and a compass with a bit of personality (swinging needle, bobbing dial) are drawn once behind the whole app. The theme background is now transparent for that reason.

## v0.12 – One budget card, hotel picker on top, things to do with costs

- Place page: the separate "Suggested daily budget" card is gone. Choosing where you'll stay now sits at the top of "Your trip here, all in", with each option showing its room price per night and the all-in daily cost.
- New "Things to do & what they cost" on every place, and per-day activities with costs in the planner's day-by-day itinerary (arrival days lighter, a total at the bottom). Sources in order: checked ideas (Jim Corbett: jeep and canter safaris, entry fees, temple, falls, museum, birdwatching), priced See/Do listings from Wikivoyage (Indian and foreign prices as a range), then typical ideas for the kind of place, labelled as typical.

## v0.11 – Wallpaper, events and reimbursement claims

- Home screen wallpaper: a vintage compass (drawn in code, needle gently sways) over faded, slowly cross-fading open-licence photos of Indian destinations (Wikipedia/Wikimedia Commons, cached on the phone, compass-only when offline).
- **Scan a bill** card on the front page: pick a trip or event (or create an event) and the receipt scanner opens straight away.
- **Events**: a trip flagged as an event (conference, client visit, offsite) with a company to claim from. No route tab; bills, people and settle-up work as for trips. Database moves to schema v2 (migration included, existing trips are kept).
- **Reimbursement PDF** (📄 in a trip or event): claimant, company and reference, itemised bills by date, category totals, payment-method split, flags bills without a receipt photo. Shared via the Android share sheet.

## Run it

1. Open this folder in Android Studio (File > Open > Packfare).
2. Let Gradle sync. Accept any offer to install SDK 35 or upgrade the Android Gradle Plugin.
3. Pick an emulator or phone and press Run.

Unit tests (split maths, receipt reading, budget planner, fares, runway, cash, Wikivoyage parsing):
right-click `app/src/test` > Run Tests, or `./gradlew test`.

## Test APKs from GitHub (no Android Studio needed)

`.github/workflows/build-apk.yml` builds a test APK on GitHub's servers on every push to `main`.

1. Push this project to your GitHub repo (see below).
2. Open the repo's **Actions** tab and watch "Build test APK" (about 5–8 minutes).
3. On your phone, open the repo's **Releases** page, download the newest `Packfare-…apk` and open it
   (allow "install unknown apps" for your browser when asked).

Optional: add `TRAVELPAYOUTS_TOKEN`, `TRAVELPAYOUTS_MARKER`, `TRAVELPAYOUTS_PROMO_12GO`,
`TRAVELPAYOUTS_PROMO_AGODA` and `TRAVELPAYOUTS_PROMO_BOOKING` under *Settings → Secrets and variables →
Actions* so test builds have live flight prices and tracked booking links. The workflow also writes an
APK size breakdown to each run's summary.

All test builds (CI and Android Studio) are signed with the bundled test key `app/packfare-test.jks`, so
each new APK installs over the last one. Before publishing on the Play Store, create a private release key.

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
   (From v0.21 also add `travelpayouts.promo12go`, `travelpayouts.promoAgoda` and `travelpayouts.promoBooking`
   with the promo ID of each program, so 12Go, Agoda and Booking.com links are tracked. Leave a line empty
   and that program's links stay plain.)
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
  fares/         FareModels (pure: picker, booking links), Affiliate (tracked links), TravelpayoutsFlights, FareSearch
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

## Recent releases (v0.16 – v0.21)

### v0.21.0
- Affiliate tracking for 12Go, Agoda and Booking.com links: set your Travelpayouts marker and the three promo IDs in `local.properties` or GitHub secrets (see "Turning on live flight prices"). Without them links stay plain. Code: `fares/Affiliate.kt`.

### v0.20.0
- Trip Today: a large **"left after your plan"** figure at the top (trip budget minus bookings, bills and the activities you still plan to do, for everyone in the group), with the daily allowance under it. The separate card at the bottom is gone.
- Weather strip: a 7-day forecast (Open-Meteo, free, no key) at the top of Today, cached for offline use.
- Tap an activity for a popup with its photo, cost, a short Wikivoyage or Wikipedia write-up and Add / Remove.

### v0.19.0
- Trip **Route** tab rebuilt like Hotels: each hop has a per-person budget, Train / Bus / Flight / Taxi options with fare, time and group total, options that fit highlighted, "Use this" to put one in the plan, and booking links per kind (IRCTC, 12Go, redBus, Google Flights, Skyscanner, ixigo, MakeMyTrip, Savaari, Ola Outstation).
- New Private taxi mode (outstation cab estimate, up to 4 people a car).

### v0.18.1
- Hotels: the budget per bed per night now really splits the guide's places into "fits" and "over your budget".
- Removed the foreign-visitor price disclaimers; entry-fee ranges for forts and monuments are wider.

### v0.18.0
- Short descriptions for places and activities from Wikipedia intros (cached, offline-safe, only shown when the article clearly matches). One credit line, no per-item citations.

### v0.17.x
- Discover: the dot map is gone. "Browse by price" lists popular places by Cheap / Mid / Pricey, cheapest first, and Near me lists the closest places.
- Trip Today plan uses real See/Do listings from Wikivoyage (named places, short description, opening hours) instead of generic filler; typical ideas only top up thin lists.
- Trip screen: Bill, Activity and Expense sit in one bottom row with matching buttons. The Wikivoyage credit is one line.

### v0.16.0
- Trip page: new **Hotels** tab per stop with Booking.com (opens filtered to your nightly budget), Agoda and Google Hotels links, plus the guide's priced places to sleep. Places that fit the budget are highlighted.
- Home: the search box is a real text field with a **Go** button; it opens Discover with your search applied. Home shows the app version.

### Coming next
Planned for v0.22 onward: world-wide places and currencies, global flight/train/bus/taxi options with a visa card, a time-aware daily calendar that adapts to your arrival time, and next-step reminders. The plan is in the shared "Packfare 1.0" document.
