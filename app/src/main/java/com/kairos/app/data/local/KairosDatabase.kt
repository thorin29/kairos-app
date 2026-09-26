package com.kairos.app.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The app's local database. Today it holds only the durable read cache
 * ([PayloadCacheEntity]); future offline-first work adds entities here.
 *
 * This DB is a CACHE, not a source of truth: everything in it is re-fetchable
 * from the API. That single fact shapes the config — we use destructive
 * migration, so bumping [version] on a schema change just drops and recreates
 * the tables (they refill on the next sync) and we never hand-write or test a
 * Room migration. If an entity is ever promoted to source-of-truth (e.g. it
 * backs offline writes), it must move out from under destructive migration.
 */
@Database(
    entities = [PayloadCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KairosDatabase : RoomDatabase() {

    abstract fun payloadCacheDao(): PayloadCacheDao

    companion object {
        private const val DB_NAME = "kairos-cache.db"

        fun build(context: Context): KairosDatabase {
            val appContext = context.applicationContext
            return Room.databaseBuilder<KairosDatabase>(
                context = appContext,
                name = appContext.getDatabasePath(DB_NAME).absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                // Cache DB: on any version change, drop and refill rather than
                // migrate. Downgrades (e.g. installing an older build over a
                // newer schema) are covered by the same rule.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
