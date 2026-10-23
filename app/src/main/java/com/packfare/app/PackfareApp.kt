package com.packfare.app

import android.app.Application
import com.packfare.app.data.AppDatabase
import com.packfare.app.data.TripRepository
import com.packfare.app.explore.ExploreRepository
import com.packfare.app.explore.WikivoyageClient
import com.packfare.app.fares.FareSearch
import com.packfare.app.fares.TravelpayoutsFlights
import com.packfare.app.receipt.ReceiptScanner
import com.packfare.app.receipt.ReceiptStorage
import com.packfare.app.vault.Vault
import com.packfare.app.util.Prefs
import org.maplibre.android.MapLibre

/**
 * Manual dependency injection: one instance of each service for the whole app.
 * Simple on purpose. Swap for Hilt later if the app grows.
 */
class PackfareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Must run before any MapView is created.
        MapLibre.getInstance(this)
    }

    private val db: AppDatabase by lazy { AppDatabase.create(this) }

    val receiptStorage: ReceiptStorage by lazy { ReceiptStorage(this) }
    val receiptScanner: ReceiptScanner by lazy { ReceiptScanner(this) }
    val repository: TripRepository by lazy { TripRepository(db.tripDao(), receiptStorage) }
    val explore: ExploreRepository by lazy { ExploreRepository(WikivoyageClient(), db.extrasDao()) }
    val vault: Vault by lazy { Vault(this, db.extrasDao()) }
    val prefs: Prefs by lazy { Prefs(this) }
    val fares: FareSearch by lazy {
        FareSearch(TravelpayoutsFlights(BuildConfig.TRAVELPAYOUTS_TOKEN, BuildConfig.TRAVELPAYOUTS_MARKER.ifBlank { null }))
    }
}
