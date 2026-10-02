package org.rpwt.wlwdw.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The message store's queries.
 *
 * Every read returns a [Flow], which is the point of the rewrite here: the old
 * `ListView` + `SimpleCursorAdapter` re-queried by hand and leaked cursors, and
 * a Flow-backed list cannot drift from what is stored or be left open.
 */
@Dao
interface MessageDao {

    /** Newest first: [MessageEntity.receivedAt] is this device's clock. */
    @Query("SELECT * FROM message ORDER BY received_at DESC, _id DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE message SET read = 1 WHERE read = 0")
    suspend fun markAllRead()
}
