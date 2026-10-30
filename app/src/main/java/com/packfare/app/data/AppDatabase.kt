package com.packfare.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class, MemberEntity::class, LegEntity::class, ExpenseEntity::class,
        ExpenseShareEntity::class, CashTopUpEntity::class, DocumentEntity::class, PlaceCacheEntity::class,
    ],
    version = 2,
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
            Room.databaseBuilder(context, AppDatabase::class.java, "packfare.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v0.11: trips can be flagged as events and carry a company for reimbursement claims. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN kind TEXT NOT NULL DEFAULT 'TRIP'")
                db.execSQL("ALTER TABLE trips ADD COLUMN company TEXT")
            }
        }
    }
}
