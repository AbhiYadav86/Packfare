package com.packfare.app.explore

import com.packfare.app.data.ExtrasDao
import com.packfare.app.data.PlaceCacheEntity
import com.packfare.app.domain.ActivityIdea
import com.packfare.app.domain.Activities
import com.packfare.app.domain.CostTier
import com.packfare.app.domain.DailyCosts
import com.packfare.app.domain.Destination
import com.packfare.app.domain.Destinations

/** A place as Explore shows it: article text + live prices + our table, merged. */
data class PlaceInfo(
    val title: String,
    val summary: String,
    val lat: Double?,
    val lon: Double?,
    val stats: PriceStats,
    /** Matching bundled destination, if any (gives permits, ATMs, tier). */
    val local: Destination?,
    val fetchedAt: Long,
    val fromCache: Boolean,
    /** Names of the guide's "See" and "Do" listings. */
    val highlights: List<String> = emptyList(),
    /** The guide's See/Do listings with whatever prices it gives. */
    val guideActivities: List<ActivityIdea> = emptyList(),
) {
    /** Things to do here with approximate costs: checked ideas, guide listings, then typical ones. */
    val activities: List<ActivityIdea>
        get() = Activities.ideasFor(asDestination(), guideActivities)

    /** Backpacker daily costs: table (or MID tier for unknown places) refined by live prices. */
    val costs: DailyCosts
        get() = WikivoyageParser.refine(local?.baseline ?: DailyCosts.forTier(CostTier.MID), stats)

    /** A Destination usable by the planner, even for places not in our table. */
    fun asDestination(): Destination = local ?: Destination(
        name = title,
        region = "Other",
        lat = lat ?: Double.NaN,
        lon = lon ?: Double.NaN,
        tier = CostTier.MID,
        wikivoyage = title,
        airport = if (lat != null && lon != null) com.packfare.app.domain.Airports.nearest(lat, lon, maxKm = 200.0)?.code else null,
    )
}

/**
 * The "crawler": fetches Wikivoyage articles on demand, extracts prices, and keeps a copy in Room
 * so everything you've explored works offline. Refreshes cached places older than a month.
 */
class ExploreRepository(
    private val client: WikivoyageClient,
    private val dao: ExtrasDao,
) {
    fun recent() = dao.recentPlaces()

    suspend fun search(query: String): List<SearchHit> = client.search(query)

    suspend fun place(title: String, forceRefresh: Boolean = false): PlaceInfo? {
        val cached = dao.cachedPlace(title)
        val fresh = cached != null && System.currentTimeMillis() - cached.fetchedAt < MAX_AGE_MS
        if (cached != null && fresh && !forceRefresh) return cached.toInfo(fromCache = true)

        val page = runCatching { client.page(title) }.getOrNull()
            ?: return cached?.toInfo(fromCache = true) // offline: fall back to the old copy
                ?: Destinations.find(title)?.let { offlineInfo(it) } // no article (or offline): Packfare's own data
        val entity = PlaceCacheEntity(page.title, page.summary, page.lat, page.lon, page.wikitext)
        dao.cachePlace(entity)
        if (page.title != title) dao.cachePlace(entity.copy(title = title)) // remember the redirect too
        return entity.toInfo(fromCache = false)
    }

    /** Live-refined daily costs for a planner stop, using only what's already cached (no network). */
    suspend fun cachedCosts(destination: Destination): Pair<DailyCosts, Int>? {
        val cached = dao.cachedPlace(destination.wikivoyage) ?: dao.cachedPlace(destination.name) ?: return null
        val stats = WikivoyageParser.parse(cached.wikitext)
        if (stats.sampleCount == 0) return null
        return WikivoyageParser.refine(destination.baseline, stats) to stats.sampleCount
    }

    /**
     * Things to do at a hub with approximate costs. Works offline: checked and typical ideas need no
     * network, and the guide's listings are added when a cached or fetched copy exists.
     */
    suspend fun activities(destination: Destination, allowNetwork: Boolean): List<ActivityIdea> {
        val cached = dao.cachedPlace(destination.wikivoyage) ?: dao.cachedPlace(destination.name)
        val guide = when {
            cached != null -> WikivoyageParser.activities(cached.wikitext)
            allowNetwork -> runCatching { place(destination.wikivoyage)?.guideActivities }.getOrNull().orEmpty()
            else -> emptyList()
        }
        return Activities.ideasFor(destination, guide)
    }

    /** The guide's priced places to sleep, cheapest first. Uses the cached article, or fetches it when allowed. */
    suspend fun stays(destination: Destination, allowNetwork: Boolean): List<PriceSample> {
        val cached = dao.cachedPlace(destination.wikivoyage) ?: dao.cachedPlace(destination.name)
        val stats = when {
            cached != null -> WikivoyageParser.parse(cached.wikitext)
            allowNetwork -> runCatching { place(destination.wikivoyage)?.stats }.getOrNull()
            else -> null
        }
        return stats?.sleep.orEmpty().sortedBy { it.pricePaise }
    }

    /** A place page built only from Packfare's table: costs, map, fares and flights still work. */
    private fun offlineInfo(d: Destination) = PlaceInfo(
        title = d.name,
        summary = "",
        lat = d.lat,
        lon = d.lon,
        stats = WikivoyageParser.parse(""),
        local = d,
        fetchedAt = System.currentTimeMillis(),
        fromCache = true,
        highlights = emptyList(),
        guideActivities = emptyList(),
    )

    private fun PlaceCacheEntity.toInfo(fromCache: Boolean) = PlaceInfo(
        title = title,
        summary = summary,
        lat = lat,
        lon = lon,
        stats = WikivoyageParser.parse(wikitext),
        local = Destinations.find(title),
        fetchedAt = fetchedAt,
        fromCache = fromCache,
        highlights = WikivoyageParser.highlights(wikitext),
        guideActivities = WikivoyageParser.activities(wikitext),
    )

    companion object {
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
