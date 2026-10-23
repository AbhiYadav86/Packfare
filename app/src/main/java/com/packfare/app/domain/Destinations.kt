package com.packfare.app.domain

/** Rough cost level of a place for budget travellers. */
enum class CostTier { LOW, MID, HIGH }

/**
 * Typical daily costs at a place, in paise.
 *
 * [stayPaise], [foodPaise], [localTransportPaise], [activitiesPaise] are the per-person BACKPACKER
 * baseline (dorm bed). Hotel styles use per-room prices: [tier] tables by default, or live
 * Wikivoyage "Mid-range"/"Splurge" medians when [midRoomPaise]/[upscaleRoomPaise] are known.
 */
data class DailyCosts(
    val stayPaise: Long,
    val foodPaise: Long,
    val localTransportPaise: Long,
    val activitiesPaise: Long,
    val tier: CostTier = CostTier.MID,
    /** Live median for 3★-ish rooms (per room per night), if known. */
    val midRoomPaise: Long? = null,
    /** Live median for 4★-ish rooms (per room per night), if known. */
    val upscaleRoomPaise: Long? = null,
) {
    val totalPaise: Long get() = stayPaise + foodPaise + localTransportPaise + activitiesPaise

    /** Per-person costs for a style. [solo] = you pay the whole hotel room. */
    fun forStyle(style: TravelStyle, solo: Boolean = false): DailyCosts {
        val stay = if (!style.rooms) {
            stayPaise
        } else {
            val room = roomPaise(style)
            if (solo) room else room / 2
        }
        return DailyCosts(
            stayPaise = stay,
            foodPaise = (foodPaise * style.food).toLong(),
            localTransportPaise = (localTransportPaise * style.local).toLong(),
            activitiesPaise = (activitiesPaise * style.activities).toLong(),
            tier = tier,
        )
    }

    /** Price of one hotel room per night for a style (paise). */
    fun roomPaise(style: TravelStyle): Long = when (style) {
        TravelStyle.BACKPACKER -> stayPaise * 2
        TravelStyle.FLASHPACKER -> budgetRoom(tier)
        TravelStyle.COMFORT -> midRoomPaise ?: midRoom(tier)
        TravelStyle.PREMIUM -> upscaleRoomPaise ?: upscaleRoom(tier)
    }

    companion object {
        /** Baseline backpacker costs per tier (₹ dorm / 3 meals / local travel / sights). Estimates, 2026. */
        fun forTier(tier: CostTier): DailyCosts = when (tier) {
            CostTier.LOW -> DailyCosts(40_000, 45_000, 15_000, 15_000, CostTier.LOW)    // ≈ ₹1,150/day
            CostTier.MID -> DailyCosts(55_000, 55_000, 20_000, 25_000, CostTier.MID)    // ≈ ₹1,550/day
            CostTier.HIGH -> DailyCosts(80_000, 70_000, 30_000, 35_000, CostTier.HIGH)  // ≈ ₹2,150/day
        }

        /** 1–2★ private room per night (₹1,200 / 1,800 / 2,800). */
        fun budgetRoom(tier: CostTier): Long = when (tier) {
            CostTier.LOW -> 1_20_000
            CostTier.MID -> 1_80_000
            CostTier.HIGH -> 2_80_000
        }

        /** 3★ room per night (₹2,800 / 4,000 / 6,000). */
        fun midRoom(tier: CostTier): Long = when (tier) {
            CostTier.LOW -> 2_80_000
            CostTier.MID -> 4_00_000
            CostTier.HIGH -> 6_00_000
        }

        /** 4★ room per night (₹5,000 / 7,500 / 11,000). */
        fun upscaleRoom(tier: CostTier): Long = when (tier) {
            CostTier.LOW -> 5_00_000
            CostTier.MID -> 7_50_000
            CostTier.HIGH -> 11_00_000
        }
    }
}

data class Destination(
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val tier: CostTier,
    /** Mountain roads: slower and longer than the straight-line distance suggests. */
    val hills: Boolean = false,
    /** A railway station within easy reach. */
    val rail: Boolean = true,
    /** Island: reached by flight or ferry only. */
    val island: Boolean = false,
    /** ATMs are scarce or unreliable: carry cash. */
    val fewAtms: Boolean = false,
    /** Permit hint shown to travellers (Inner Line Permit, Protected Area Permit...). */
    val permitNote: String? = null,
    /** Wikivoyage article title, for Explore. */
    val wikivoyage: String = name,
    /** Nearest airport with regular flights (IATA). Null = work it out from coordinates ([Airports.candidatesFor]). */
    val airport: String? = null,
    /** Hand-tuned backpacker hub (themes, itinerary builder). False for the 800+ extra searchable towns. */
    val hub: Boolean = true,
) {
    val baseline: DailyCosts get() = DailyCosts.forTier(tier)

    /** Own or nearest airport within reach (IATA), e.g. Kullu -> KUU. */
    val nearestAirport: String? get() = FlightPlanner.candidatesFor(this).firstOrNull()?.code
}

/**
 * Bundled places: backpacker hubs plus big cities and popular towns (e.g. Ahmedabad, Pune, Lucknow)
 * for every travel style. Costs are tier-based estimates; Explore refines them with live prices
 * from Wikivoyage when online. Shipping this in the app keeps planning fully offline.
 * Coordinates are town centres (±1 km), good enough for maps and distance estimates.
 */
object Destinations {

    private const val HP = "Himachal"
    private const val UK = "Uttarakhand"
    private const val LA = "Ladakh"
    private const val JK = "Jammu & Kashmir"
    private const val PB = "Punjab, Haryana & Chandigarh"
    private const val UP = "Delhi & Uttar Pradesh"
    private const val RJ = "Rajasthan"
    private const val GJ = "Gujarat"
    private const val MH = "Maharashtra"
    private const val MP = "Madhya Pradesh & Chhattisgarh"
    private const val GOA = "Goa & Konkan"
    private const val KA = "Karnataka"
    private const val KL = "Kerala"
    private const val TN = "Tamil Nadu & Puducherry"
    private const val AP = "Andhra & Telangana"
    private const val OD = "Odisha"
    private const val EAST = "West Bengal & Sikkim"
    private const val BH = "Bihar & Jharkhand"
    private const val NE = "Northeast"
    private const val AN = "Andaman"
    private const val LD = "Lakshadweep"

    private const val ILP_ARUNACHAL = "Inner Line Permit needed for Arunachal (Indians: e-ILP online; foreigners: Protected Area Permit)."
    private const val ILP_NAGALAND = "Inner Line Permit needed for Indian visitors (e-ILP online); foreigners register on arrival."
    private const val ILP_MIZORAM = "Inner Line Permit needed for Indian visitors; foreigners register within 24 hours."
    private const val ILP_MANIPUR = "Inner Line Permit needed for Indian visitors; check current entry rules before you go."
    private const val SIKKIM = "Foreigners need a Restricted Area Permit for Sikkim; extra permits for some valleys."
    private const val NORTH_SIKKIM = "Protected Area Permit for North Sikkim, arranged through a registered tour operator."
    private const val LADAKH_PAP = "Indians need an Inner Line Permit and foreigners a Protected Area Permit (online, Leh) for this area."
    private const val LAKSHADWEEP = "Entry permit required for Lakshadweep (apply online through the Lakshadweep administration)."

    /** The hand-tuned hubs: richest data, used by the itinerary builder. */
    val hubs: List<Destination> = listOf(
        // ---- Himachal ----
        Destination("Manali", HP, 32.24, 77.19, CostTier.MID, hills = true, rail = false, airport = "KUU"),
        Destination("Kasol", HP, 32.01, 77.31, CostTier.LOW, hills = true, rail = false, airport = "KUU"),
        Destination("Tirthan Valley", HP, 31.64, 77.45, CostTier.LOW, hills = true, rail = false, fewAtms = true, airport = "KUU"),
        Destination("McLeod Ganj", HP, 32.24, 76.32, CostTier.LOW, hills = true, rail = false, airport = "DHM"),
        Destination("Bir", HP, 32.04, 76.72, CostTier.LOW, hills = true, rail = false, wikivoyage = "Bir (India)", airport = "DHM"),
        Destination("Dalhousie", HP, 32.54, 75.97, CostTier.MID, hills = true, rail = false, airport = "DHM"),
        Destination("Shimla", HP, 31.10, 77.17, CostTier.MID, hills = true, airport = "SLV"),
        Destination("Kasauli", HP, 30.90, 76.97, CostTier.MID, hills = true, rail = false, airport = "IXC"),
        Destination("Kaza (Spiti)", HP, 32.23, 78.07, CostTier.MID, hills = true, rail = false, fewAtms = true,
            permitNote = "Foreigners need an Inner Line Permit for parts of Kinnaur–Spiti.", wikivoyage = "Kaza"),
        // ---- Uttarakhand ----
        Destination("Rishikesh", UK, 30.09, 78.27, CostTier.LOW, airport = "DED"),
        Destination("Haridwar", UK, 29.95, 78.16, CostTier.LOW, airport = "DED"),
        Destination("Dehradun", UK, 30.32, 78.03, CostTier.MID, airport = "DED"),
        Destination("Mussoorie", UK, 30.46, 78.07, CostTier.MID, hills = true, rail = false, airport = "DED"),
        Destination("Nainital", UK, 29.38, 79.46, CostTier.MID, hills = true, rail = false, airport = "PGH"),
        Destination("Almora", UK, 29.60, 79.66, CostTier.LOW, hills = true, rail = false, airport = "PGH"),
        Destination("Jim Corbett", UK, 29.39, 79.13, CostTier.HIGH, wikivoyage = "Jim Corbett National Park", airport = "PGH"),
        Destination("Auli", UK, 30.53, 79.56, CostTier.MID, hills = true, rail = false, fewAtms = true, airport = "DED"),
        // ---- Ladakh ----
        Destination("Leh", LA, 34.16, 77.58, CostTier.HIGH, hills = true, rail = false, airport = "IXL",
            permitNote = "Inner Line / Protected Area Permit needed for Nubra, Pangong and other valleys."),
        Destination("Nubra Valley", LA, 34.60, 77.55, CostTier.HIGH, hills = true, rail = false, fewAtms = true, airport = "IXL",
            permitNote = "Inner Line / Protected Area Permit needed (get it in Leh or online)."),
        // ---- Jammu & Kashmir ----
        Destination("Srinagar", JK, 34.08, 74.80, CostTier.MID, hills = true, airport = "SXR"),
        Destination("Gulmarg", JK, 34.05, 74.38, CostTier.HIGH, hills = true, rail = false, airport = "SXR"),
        Destination("Pahalgam", JK, 34.02, 75.32, CostTier.MID, hills = true, rail = false, airport = "SXR"),
        Destination("Sonamarg", JK, 34.30, 75.29, CostTier.MID, hills = true, rail = false, fewAtms = true, airport = "SXR"),
        Destination("Jammu", JK, 32.73, 74.86, CostTier.LOW, airport = "IXJ"),
        Destination("Katra (Vaishno Devi)", JK, 32.99, 74.93, CostTier.LOW, hills = true, wikivoyage = "Katra", airport = "IXJ"),
        // ---- Punjab & Chandigarh ----
        Destination("Amritsar", PB, 31.63, 74.87, CostTier.LOW, airport = "ATQ"),
        Destination("Chandigarh", PB, 30.73, 76.78, CostTier.MID, airport = "IXC"),
        // ---- Delhi & Uttar Pradesh ----
        Destination("Delhi", UP, 28.61, 77.21, CostTier.MID, airport = "DEL"),
        Destination("Agra", UP, 27.18, 78.01, CostTier.MID, airport = "AGR"),
        Destination("Vrindavan", UP, 27.58, 77.70, CostTier.LOW, airport = "AGR"),
        Destination("Lucknow", UP, 26.85, 80.95, CostTier.MID, airport = "LKO"),
        Destination("Ayodhya", UP, 26.80, 82.20, CostTier.LOW, airport = "AYJ"),
        Destination("Prayagraj", UP, 25.44, 81.85, CostTier.LOW, airport = "IXD"),
        Destination("Varanasi", UP, 25.32, 83.01, CostTier.LOW, airport = "VNS"),
        // ---- Rajasthan ----
        Destination("Jaipur", RJ, 26.91, 75.79, CostTier.MID, airport = "JAI"),
        Destination("Mandawa", RJ, 28.05, 75.15, CostTier.LOW, rail = false, airport = "JAI"),
        Destination("Ranthambore", RJ, 26.02, 76.35, CostTier.HIGH, wikivoyage = "Ranthambore National Park", airport = "JAI"),
        Destination("Bundi", RJ, 25.44, 75.64, CostTier.LOW, airport = "JAI"),
        Destination("Pushkar", RJ, 26.49, 74.55, CostTier.LOW, airport = "KQH"),
        Destination("Udaipur", RJ, 24.59, 73.71, CostTier.MID, airport = "UDR"),
        Destination("Chittorgarh", RJ, 24.88, 74.62, CostTier.LOW, airport = "UDR"),
        Destination("Mount Abu", RJ, 24.59, 72.71, CostTier.MID, hills = true, rail = false, airport = "UDR"),
        Destination("Jodhpur", RJ, 26.24, 73.02, CostTier.MID, airport = "JDH"),
        Destination("Jaisalmer", RJ, 26.92, 70.91, CostTier.LOW, airport = "JSA"),
        Destination("Bikaner", RJ, 28.02, 73.31, CostTier.LOW, airport = "BKB"),
        // ---- Gujarat ----
        Destination("Ahmedabad", GJ, 23.03, 72.58, CostTier.MID, airport = "AMD"),
        Destination("Vadodara", GJ, 22.31, 73.18, CostTier.MID, airport = "BDQ"),
        Destination("Statue of Unity", GJ, 21.84, 73.72, CostTier.MID, wikivoyage = "Kevadia", airport = "BDQ"),
        Destination("Surat", GJ, 21.17, 72.83, CostTier.MID, airport = "STV"),
        Destination("Rajkot", GJ, 22.30, 70.80, CostTier.MID, airport = "HSR"),
        Destination("Dwarka", GJ, 22.24, 68.97, CostTier.LOW, airport = "PBD"),
        Destination("Somnath", GJ, 20.89, 70.40, CostTier.LOW, airport = "DIU"),
        Destination("Sasan Gir", GJ, 21.17, 70.60, CostTier.HIGH, rail = false, airport = "DIU"),
        Destination("Diu", GJ, 20.72, 70.98, CostTier.MID, rail = false, airport = "DIU"),
        Destination("Bhuj", GJ, 23.24, 69.67, CostTier.LOW, airport = "BHJ"),
        Destination("Rann of Kutch", GJ, 23.83, 69.67, CostTier.MID, rail = false, fewAtms = true, airport = "BHJ"),
        // ---- Maharashtra ----
        Destination("Mumbai", MH, 19.08, 72.88, CostTier.HIGH, airport = "BOM"),
        Destination("Alibaug", MH, 18.64, 72.87, CostTier.MID, rail = false, airport = "BOM"),
        Destination("Pune", MH, 18.52, 73.86, CostTier.MID, airport = "PNQ"),
        Destination("Lonavala", MH, 18.75, 73.41, CostTier.MID, hills = true, airport = "PNQ"),
        Destination("Mahabaleshwar", MH, 17.92, 73.66, CostTier.MID, hills = true, rail = false, airport = "PNQ"),
        Destination("Nashik", MH, 20.00, 73.79, CostTier.LOW, airport = "ISK"),
        Destination("Aurangabad (Ajanta & Ellora)", MH, 19.88, 75.34, CostTier.LOW, wikivoyage = "Aurangabad", airport = "IXU"),
        Destination("Kolhapur", MH, 16.70, 74.24, CostTier.LOW, airport = "KLH"),
        Destination("Tarkarli (Malvan)", MH, 16.03, 73.47, CostTier.MID, rail = false, wikivoyage = "Malvan", airport = "GOX"),
        Destination("Nagpur", MH, 21.15, 79.09, CostTier.MID, airport = "NAG"),
        // ---- Madhya Pradesh & Chhattisgarh ----
        Destination("Gwalior", MP, 26.22, 78.18, CostTier.LOW, airport = "GWL"),
        Destination("Orchha", MP, 25.35, 78.64, CostTier.LOW, rail = false, airport = "GWL"),
        Destination("Khajuraho", MP, 24.85, 79.93, CostTier.LOW, airport = "HJR"),
        Destination("Bhopal", MP, 23.26, 77.41, CostTier.MID, airport = "BHO"),
        Destination("Indore", MP, 22.72, 75.86, CostTier.MID, airport = "IDR"),
        Destination("Ujjain", MP, 23.18, 75.78, CostTier.LOW, airport = "IDR"),
        Destination("Mandu", MP, 22.34, 75.40, CostTier.LOW, rail = false, airport = "IDR"),
        Destination("Pachmarhi", MP, 22.47, 78.43, CostTier.MID, hills = true, rail = false, airport = "BHO"),
        Destination("Jabalpur", MP, 23.18, 79.99, CostTier.LOW, airport = "JLR"),
        Destination("Kanha", MP, 22.33, 80.61, CostTier.HIGH, rail = false, fewAtms = true, wikivoyage = "Kanha National Park", airport = "JLR"),
        Destination("Bandhavgarh", MP, 23.70, 81.03, CostTier.HIGH, rail = false, fewAtms = true,
            wikivoyage = "Bandhavgarh National Park", airport = "JLR"),
        Destination("Raipur", MP, 21.25, 81.63, CostTier.LOW, airport = "RPR"),
        Destination("Jagdalpur (Bastar)", MP, 19.08, 82.02, CostTier.LOW, wikivoyage = "Jagdalpur", airport = "JGB"),
        // ---- Goa & Konkan ----
        Destination("Goa (North)", GOA, 15.58, 73.74, CostTier.HIGH, wikivoyage = "North Goa", airport = "GOX"),
        Destination("Goa (South)", GOA, 15.01, 74.02, CostTier.HIGH, wikivoyage = "South Goa", airport = "GOI"),
        Destination("Gokarna", GOA, 14.55, 74.32, CostTier.MID, airport = "GOI"),
        // ---- Karnataka ----
        Destination("Bengaluru", KA, 12.97, 77.59, CostTier.MID, wikivoyage = "Bangalore", airport = "BLR"),
        Destination("Mysuru", KA, 12.30, 76.64, CostTier.LOW, wikivoyage = "Mysore", airport = "MYQ"),
        Destination("Coorg", KA, 12.42, 75.74, CostTier.MID, hills = true, rail = false, wikivoyage = "Kodagu", airport = "CNN"),
        Destination("Chikmagalur", KA, 13.32, 75.77, CostTier.MID, hills = true, airport = "IXE"),
        Destination("Sakleshpur", KA, 12.94, 75.79, CostTier.LOW, hills = true, airport = "IXE"),
        Destination("Mangaluru", KA, 12.91, 74.86, CostTier.MID, wikivoyage = "Mangalore", airport = "IXE"),
        Destination("Udupi", KA, 13.34, 74.75, CostTier.LOW, airport = "IXE"),
        Destination("Murudeshwar", KA, 14.09, 74.48, CostTier.LOW, airport = "IXE"),
        Destination("Dandeli", KA, 15.25, 74.62, CostTier.MID, rail = false, airport = "HBX"),
        Destination("Hampi", KA, 15.33, 76.46, CostTier.LOW, airport = "HBX"),
        Destination("Badami", KA, 15.92, 75.68, CostTier.LOW, airport = "HBX"),
        // ---- Kerala ----
        Destination("Kochi", KL, 9.93, 76.27, CostTier.MID, airport = "COK"),
        Destination("Munnar", KL, 10.09, 77.06, CostTier.MID, hills = true, rail = false, airport = "COK"),
        Destination("Thekkady", KL, 9.60, 77.16, CostTier.MID, hills = true, rail = false, airport = "COK"),
        Destination("Alleppey", KL, 9.50, 76.34, CostTier.MID, wikivoyage = "Alappuzha", airport = "COK"),
        Destination("Kumarakom", KL, 9.62, 76.43, CostTier.HIGH, rail = false, airport = "COK"),
        Destination("Thiruvananthapuram", KL, 8.52, 76.94, CostTier.MID, airport = "TRV"),
        Destination("Varkala", KL, 8.73, 76.71, CostTier.MID, airport = "TRV"),
        Destination("Kovalam", KL, 8.40, 76.98, CostTier.MID, airport = "TRV"),
        Destination("Kollam", KL, 8.89, 76.61, CostTier.LOW, airport = "TRV"),
        Destination("Kozhikode", KL, 11.26, 75.78, CostTier.LOW, airport = "CCJ"),
        Destination("Wayanad", KL, 11.61, 76.08, CostTier.MID, hills = true, rail = false, airport = "CCJ"),
        Destination("Kannur", KL, 11.87, 75.37, CostTier.LOW, airport = "CNN"),
        Destination("Bekal", KL, 12.39, 75.03, CostTier.MID, airport = "CNN"),
        // ---- Tamil Nadu & Puducherry ----
        Destination("Chennai", TN, 13.08, 80.27, CostTier.MID, airport = "MAA"),
        Destination("Mahabalipuram", TN, 12.62, 80.19, CostTier.LOW, rail = false, wikivoyage = "Mamallapuram", airport = "MAA"),
        Destination("Kanchipuram", TN, 12.83, 79.70, CostTier.LOW, airport = "MAA"),
        Destination("Pondicherry", TN, 11.94, 79.81, CostTier.MID, airport = "MAA"),
        Destination("Tiruvannamalai", TN, 12.23, 79.07, CostTier.LOW, airport = "MAA"),
        Destination("Coimbatore", TN, 11.02, 76.96, CostTier.MID, airport = "CJB"),
        Destination("Ooty", TN, 11.41, 76.70, CostTier.MID, hills = true, airport = "CJB"),
        Destination("Coonoor", TN, 11.35, 76.80, CostTier.MID, hills = true, airport = "CJB"),
        Destination("Valparai", TN, 10.33, 76.95, CostTier.LOW, hills = true, rail = false, airport = "CJB"),
        Destination("Yercaud", TN, 11.78, 78.21, CostTier.LOW, hills = true, rail = false, airport = "CJB"),
        Destination("Kodaikanal", TN, 10.24, 77.49, CostTier.MID, hills = true, rail = false, airport = "IXM"),
        Destination("Tiruchirappalli", TN, 10.79, 78.70, CostTier.LOW, airport = "TRZ"),
        Destination("Thanjavur", TN, 10.79, 79.14, CostTier.LOW, airport = "TRZ"),
        Destination("Chettinad (Karaikudi)", TN, 10.07, 78.78, CostTier.MID, wikivoyage = "Karaikudi", airport = "IXM"),
        Destination("Madurai", TN, 9.93, 78.12, CostTier.LOW, airport = "IXM"),
        Destination("Rameswaram", TN, 9.29, 79.31, CostTier.LOW, airport = "IXM"),
        Destination("Kanyakumari", TN, 8.08, 77.54, CostTier.LOW, airport = "TRV"),
        // ---- Andhra & Telangana ----
        Destination("Hyderabad", AP, 17.39, 78.49, CostTier.MID, airport = "HYD"),
        Destination("Warangal", AP, 17.97, 79.59, CostTier.LOW, airport = "HYD"),
        Destination("Vijayawada", AP, 16.51, 80.65, CostTier.MID, airport = "VGA"),
        Destination("Visakhapatnam", AP, 17.69, 83.22, CostTier.MID, airport = "VTZ"),
        Destination("Araku Valley", AP, 18.33, 82.88, CostTier.LOW, hills = true, airport = "VTZ"),
        Destination("Gandikota", AP, 14.81, 78.29, CostTier.LOW, rail = false, fewAtms = true),
        Destination("Tirupati", AP, 13.63, 79.42, CostTier.LOW, airport = "TIR"),
        // ---- Odisha ----
        Destination("Bhubaneswar", OD, 20.30, 85.82, CostTier.LOW, airport = "BBI"),
        Destination("Puri", OD, 19.81, 85.83, CostTier.LOW, airport = "BBI"),
        Destination("Konark", OD, 19.89, 86.09, CostTier.LOW, rail = false, airport = "BBI"),
        Destination("Chilika Lake", OD, 19.67, 85.45, CostTier.LOW, rail = false, fewAtms = true, airport = "BBI"),
        Destination("Koraput", OD, 18.81, 82.71, CostTier.LOW, hills = true, fewAtms = true, airport = "JGB"),
        // ---- West Bengal & Sikkim ----
        Destination("Kolkata", EAST, 22.57, 88.36, CostTier.MID, airport = "CCU"),
        Destination("Sundarbans", EAST, 22.16, 88.81, CostTier.MID, rail = false, fewAtms = true,
            wikivoyage = "Sundarbans National Park", airport = "CCU"),
        Destination("Digha", EAST, 21.63, 87.51, CostTier.LOW, airport = "CCU"),
        Destination("Bishnupur", EAST, 23.08, 87.32, CostTier.LOW, airport = "RDP"),
        Destination("Shantiniketan", EAST, 23.68, 87.68, CostTier.LOW, wikivoyage = "Santiniketan", airport = "RDP"),
        Destination("Siliguri", EAST, 26.73, 88.40, CostTier.MID, airport = "IXB"),
        Destination("Darjeeling", EAST, 27.04, 88.26, CostTier.MID, hills = true, airport = "IXB"),
        Destination("Kalimpong", EAST, 27.06, 88.47, CostTier.MID, hills = true, rail = false, airport = "IXB"),
        Destination("Gangtok", EAST, 27.33, 88.61, CostTier.MID, hills = true, rail = false, permitNote = SIKKIM, airport = "IXB"),
        Destination("Pelling", EAST, 27.30, 88.24, CostTier.MID, hills = true, rail = false, permitNote = SIKKIM, airport = "IXB"),
        Destination("Lachung", EAST, 27.69, 88.74, CostTier.MID, hills = true, rail = false, fewAtms = true, permitNote = NORTH_SIKKIM),
        // ---- Bihar & Jharkhand ----
        Destination("Patna", BH, 25.59, 85.14, CostTier.MID, airport = "PAT"),
        Destination("Bodh Gaya", BH, 24.70, 84.99, CostTier.LOW, airport = "GAY"),
        Destination("Rajgir", BH, 25.03, 85.42, CostTier.LOW, airport = "PAT"),
        Destination("Deoghar", BH, 24.49, 86.70, CostTier.LOW, airport = "DGH"),
        Destination("Ranchi", BH, 23.34, 85.31, CostTier.LOW, airport = "IXR"),
        Destination("Netarhat", BH, 23.48, 84.27, CostTier.LOW, hills = true, rail = false, fewAtms = true, airport = "IXR"),
        // ---- Northeast ----
        Destination("Guwahati", NE, 26.14, 91.74, CostTier.MID, airport = "GAU"),
        Destination("Tezpur", NE, 26.63, 92.80, CostTier.LOW, airport = "TEZ"),
        Destination("Kaziranga", NE, 26.58, 93.40, CostTier.MID, rail = false, wikivoyage = "Kaziranga National Park", airport = "JRH"),
        Destination("Majuli", NE, 26.95, 94.17, CostTier.LOW, rail = false, fewAtms = true, airport = "JRH"),
        Destination("Sivasagar", NE, 26.98, 94.64, CostTier.LOW, airport = "JRH"),
        Destination("Dibrugarh", NE, 27.47, 94.91, CostTier.MID, airport = "DIB"),
        Destination("Shillong", NE, 25.58, 91.89, CostTier.MID, hills = true, rail = false, airport = "GAU"),
        Destination("Cherrapunji", NE, 25.27, 91.73, CostTier.MID, hills = true, rail = false, fewAtms = true, wikivoyage = "Sohra", airport = "GAU"),
        Destination("Dawki", NE, 25.18, 92.02, CostTier.LOW, hills = true, rail = false, fewAtms = true, airport = "GAU"),
        Destination("Mawlynnong", NE, 25.20, 91.92, CostTier.LOW, hills = true, rail = false, fewAtms = true, airport = "GAU"),
        Destination("Kohima", NE, 25.67, 94.11, CostTier.MID, hills = true, rail = false, permitNote = ILP_NAGALAND, airport = "DMU"),
        Destination("Imphal", NE, 24.82, 93.94, CostTier.MID, rail = false, permitNote = ILP_MANIPUR, airport = "IMF"),
        Destination("Aizawl", NE, 23.73, 92.72, CostTier.MID, hills = true, rail = false, permitNote = ILP_MIZORAM, airport = "AJL"),
        Destination("Agartala", NE, 23.83, 91.29, CostTier.LOW, airport = "IXA"),
        Destination("Itanagar", NE, 27.09, 93.62, CostTier.MID, hills = true, permitNote = ILP_ARUNACHAL, airport = "HGI"),
        Destination("Tawang", NE, 27.59, 91.86, CostTier.MID, hills = true, rail = false, fewAtms = true, permitNote = ILP_ARUNACHAL, airport = "TEZ"),
        Destination("Dirang", NE, 27.36, 92.24, CostTier.LOW, hills = true, rail = false, fewAtms = true, permitNote = ILP_ARUNACHAL, airport = "TEZ"),
        Destination("Ziro", NE, 27.54, 93.83, CostTier.MID, hills = true, rail = false, fewAtms = true, permitNote = ILP_ARUNACHAL, airport = "HGI"),
        Destination("Mechuka", NE, 28.60, 94.12, CostTier.MID, hills = true, rail = false, fewAtms = true, permitNote = ILP_ARUNACHAL),
        // ---- Islands ----
        Destination("Port Blair", AN, 11.62, 92.73, CostTier.HIGH, rail = false, island = true, airport = "IXZ"),
        Destination("Havelock (Swaraj Dweep)", AN, 11.97, 93.00, CostTier.HIGH, rail = false, island = true,
            fewAtms = true, wikivoyage = "Havelock Island", airport = "IXZ"),
        Destination("Neil Island (Shaheed Dweep)", AN, 11.83, 93.03, CostTier.HIGH, rail = false, island = true,
            fewAtms = true, wikivoyage = "Neil Island", airport = "IXZ"),
        Destination("Agatti (Lakshadweep)", LD, 10.85, 72.19, CostTier.HIGH, rail = false, island = true,
            fewAtms = true, permitNote = LAKSHADWEEP, wikivoyage = "Agatti", airport = "AGX"),
    )

    private val regionCodes = mapOf(
        "HP" to HP, "UK" to UK, "LA" to LA, "JK" to JK, "PB" to PB, "UP" to UP, "RJ" to RJ, "GJ" to GJ,
        "MH" to MH, "MP" to MP, "GOA" to GOA, "KA" to KA, "KL" to KL, "TN" to TN, "AP" to AP, "OD" to OD,
        "EAST" to EAST, "BH" to BH, "NE" to NE, "AN" to AN, "LD" to LD,
    )
    private val permitCodes = mapOf(
        "L" to LADAKH_PAP, "S" to SIKKIM, "N" to NORTH_SIKKIM, "A" to ILP_ARUNACHAL, "G" to ILP_NAGALAND,
        "M" to ILP_MANIPUR, "Z" to ILP_MIZORAM, "D" to LAKSHADWEEP,
    )

    /** Every searchable place: the hubs first, then ~825 more towns and sights (about 1,000 in all). */
    val all: List<Destination> by lazy { hubs + ExtraDestinations.parse(regionCodes, permitCodes) }

    val regions: List<String> get() = hubs.map { it.region }.distinct()

    /** Hubs first, then the other towns in that region, A–Z. */
    fun inRegion(region: String) = all.filter { it.region == region }.sortedWith(compareBy({ !it.hub }, { if (it.hub) "" else it.name }))

    fun hubsIn(region: String) = hubs.filter { it.region == region }

    /** Case-insensitive lookup by name or Wikivoyage title; then a careful partial match ("spiti" -> Kaza (Spiti)). */
    fun find(name: String): Destination? {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return null
        return all.firstOrNull { it.name.lowercase() == q || it.wikivoyage.lowercase() == q }
            ?: all.firstOrNull { it.name.substringBefore(" (").lowercase() == q }
            ?: hubs.firstOrNull { it.name.lowercase().contains(q) && q.length >= 4 }
            ?: hubs.firstOrNull { it.name.length >= 5 && q.contains(it.name.lowercase()) }
    }

    fun search(query: String, limit: Int = 8): List<Destination> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all.filter { it.name.lowercase().contains(q) || it.region.lowercase().contains(q) }
            .sortedWith(compareBy({ if (it.name.lowercase().startsWith(q)) 0 else 1 }, { !it.hub }, { it.name.length }))
            .take(limit)
    }

    /** Hubs sorted by distance from a point, with km. */
    fun nearest(lat: Double, lon: Double, limit: Int = 5): List<Pair<Destination, Int>> =
        all.map { it to FareEstimator.haversineKm(lat, lon, it.lat, it.lon).toInt() }
            .sortedBy { it.second }
            .take(limit)
}
