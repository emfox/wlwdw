package org.rpwt.wlwdw.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The local store: received messages and the report log.
 *
 * A new file rather than the pre-rewrite `msg.db`: that one was created by a
 * `SQLiteOpenHelper` with no read state and no arrival time, so opening it
 * through Room would mean a migration that has to invent both for every old
 * row. Message history does not survive the rewrite -- the settings and the
 * device id do (see `data/prefs`), because those are load-bearing and this is
 * not.
 */
@Database(
    entities = [MessageEntity::class, ReportEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class WlwdwDatabase : RoomDatabase() {

    abstract fun messages(): MessageDao

    abstract fun reports(): ReportDao

    companion object {
        private const val NAME = "wlwdw.db"

        /**
         * v1 -> v2: the report log.
         *
         * A new table and nothing else, so there is no data to move and no
         * chance of getting a value wrong -- which is the reason to write this
         * rather than let the file be cleared. The message table is the one
         * thing here worth keeping, and it is untouched.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `report` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "`latitude` REAL, " +
                        "`longitude` REAL, " +
                        "`accuracy` REAL, " +
                        "`provider` TEXT, " +
                        "`failure` TEXT, " +
                        "`detail` TEXT)",
                )
            }
        }

        @Volatile
        private var instance: WlwdwDatabase? = null

        /**
         * The one database handle for the process.
         *
         * Room's own builder is thread-safe but creating two instances over the
         * same file is not free, and the double-checked lock is enough for a
         * single-process app with no DI container yet.
         */
        fun get(context: Context): WlwdwDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WlwdwDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
