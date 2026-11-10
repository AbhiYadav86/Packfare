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
        ActivityEntity::class,
    ],
    version = 4,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v0.11: trips can be flagged as events and carry a company for reimbursement claims. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN kind TEXT NOT NULL DEFAULT 'TRIP'")
                db.execSQL("ALTER TABLE trips ADD COLUMN company TEXT")
            }
        }

        /** v0.13: a booking checklist travels with the trip. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN bookings TEXT")
            }
        }

        /** v0.15: activities on a trip, documents that belong to a trip, and the "already booked?" flag. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN bookingsAsked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN tripId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tripId` INTEGER NOT NULL, `title` TEXT NOT NULL, `dayEpoch` INTEGER, `costPaise` INTEGER NOT NULL, " +
                        "`note` TEXT, `done` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_tripId` ON `activities` (`tripId`)")
            }
        }
    }
}
