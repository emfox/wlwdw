package org.rpwt.wlwdw.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One stored report attempt.
 *
 * The same shape as the domain value it comes from (`data/model/Report`), which
 * is not a coincidence -- but the failure is flattened into its [code] and
 * [detail] here, because the stored form has to stay readable after the Kotlin
 * type changes. See `ReportFailure.code`.
 *
 * The table is a log and is therefore capped (see `ReportRepository`): a device
 * reporting every two minutes would otherwise write a quarter of a million rows
 * a year, and nobody has ever wanted to read last March's.
 */
@Entity(tableName = "report")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,

    /**
     * When the attempt was made, on this device's clock.
     *
     * Taken before the fix and the upload, so it is the start of the attempt and
     * not the moment the answer came back -- the elapsed time of an attempt is
     * then the gap to the next row.
     */
    @ColumnInfo(name = "at")
    val at: Long,

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    /** Horizontal accuracy in metres, when the provider reported one. */
    @ColumnInfo(name = "accuracy")
    val accuracy: Float? = null,

    @ColumnInfo(name = "provider")
    val provider: String? = null,

    /** One of `ReportFailure.code`; null means the server accepted the position. */
    @ColumnInfo(name = "failure")
    val failure: String? = null,

    /** The server's or the runtime's own words, for the failures that have any. */
    @ColumnInfo(name = "detail")
    val detail: String? = null,
)
