package com.packfare.app.explore

import com.packfare.app.data.ExtrasDao
import com.packfare.app.data.PlaceCacheEntity
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
) {
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

    /** See/Do highlights for a hub: cached copy if we have one, otherwise fetched (when online). */
    suspend fun highlights(destination: Destination, allowNetwork: Boolean): List<String> {
        val cached = dao.cachedPlace(destination.wikivoyage) ?: dao.cachedPlace(destination.name)
        if (cached != null) return WikivoyageParser.highlights(cached.wikitext)
        if (!allowNetwork) return emptyList()
        return runCatching { place(destination.wikivoyage)?.highlights }.getOrNull().orEmpty()
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
    )

    companion object {
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
