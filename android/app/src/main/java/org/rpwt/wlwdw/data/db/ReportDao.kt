package org.rpwt.wlwdw.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The report log's queries.
 *
 * Deliberately four: write a row, read the last accepted one, list the log for
 * the diagnostics page, and drop what is past the cap.
 */
@Dao
interface ReportDao {

    @Insert
    suspend fun insert(record: ReportEntity): Long

    /**
     * When the last report was accepted.
     *
     * `at`, not `_id`: the clock is what the screen shows, and the two can
     * disagree if the device's time was corrected between two attempts.
     */
    @Query("SELECT MAX(at) FROM report WHERE failure IS NULL")
    suspend fun lastAcceptedAt(): Long?

    /**
     * The whole log, newest first.
     *
     * Unbounded because the table is already capped by [prune]: that cap is
     * this query's bound, and the diagnostics page counts what it lists -- a
     * header reading "1440" over a list of 200 would be its own kind of lie.
     *
     * Newest first by `_id`, the order rows were written, rather than by `at`:
     * the device clock can be corrected backwards between two attempts, and a
     * log that silently reorders itself when that happens is harder to read
     * than one that stays in the order things actually happened in.
     */
    @Query("SELECT * FROM report ORDER BY _id DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    /**
     * Keep the newest [keep] rows.
     *
     * Count rather than age: it is the row count that has to be bounded, and
     * a time window cannot bound it -- the same week is 10 080 rows at the
     * shortest interval this app offers.
     */
    @Query(
        "DELETE FROM report WHERE _id NOT IN " +
            "(SELECT _id FROM report ORDER BY _id DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)
}
