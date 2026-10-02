package org.rpwt.wlwdw.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The local message store.
 *
 * A new file rather than the pre-rewrite `msg.db`: that one was created by a
 * `SQLiteOpenHelper` with no read state and no arrival time, so opening it
 * through Room would mean a migration that has to invent both for every old
 * row. Message history does not survive the rewrite -- the settings and the
 * device id do (see `data/prefs`), because those are load-bearing and this is
 * not.
 */
@Database(entities = [MessageEntity::class], version = 1, exportSchema = true)
abstract class WlwdwDatabase : RoomDatabase() {

    abstract fun messages(): MessageDao

    companion object {
        private const val NAME = "wlwdw.db"

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
                ).build().also { instance = it }
            }
    }
}
