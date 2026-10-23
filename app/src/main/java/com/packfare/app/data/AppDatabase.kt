package com.packfare.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class, MemberEntity::class, LegEntity::class, ExpenseEntity::class,
        ExpenseShareEntity::class, CashTopUpEntity::class, DocumentEntity::class, PlaceCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun extrasDao(): ExtrasDao

    companion object {
        /**
         * Packfare starts a fresh schema (new app ID, so nothing to migrate from Desi Yatra).
         * From here on, every schema change needs a Migration, never a destructive reset.
         */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "packfare.db").build()
    }
}
