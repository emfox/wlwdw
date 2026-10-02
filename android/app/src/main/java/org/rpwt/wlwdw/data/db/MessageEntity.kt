package org.rpwt.wlwdw.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One stored message.
 *
 * Two timestamps, and they answer different questions:
 *
 * - [time] is the server's own text (`Y-m-d H:i:s`, no zone) and is kept
 *   verbatim because it is what gets displayed. Parsing it would mean deciding
 *   which zone the server meant, and getting that wrong is worse than not
 *   parsing it.
 * - [receivedAt] is when this device stored the message, in epoch millis. It is
 *   what the day grouping and the ordering use, because they are questions
 *   about this device's day, not the server's.
 */
@Entity(tableName = "message")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,

    /** The server's timestamp text, shown as-is. */
    @ColumnInfo(name = "time")
    val time: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "read", defaultValue = "0")
    val read: Boolean = false,
)
