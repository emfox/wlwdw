package org.rpwt.wlwdw.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * The report log's queries.
 *
 * Deliberately three: write a row, read the last accepted one, and drop what is
 * past the cap. The diagnostics page that will list the rows is not built yet,
 * and a query nobody calls is a query nobody has checked.
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
