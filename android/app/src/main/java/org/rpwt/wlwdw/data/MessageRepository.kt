package org.rpwt.wlwdw.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.rpwt.wlwdw.data.db.MessageEntity
import org.rpwt.wlwdw.data.db.WlwdwDatabase
import org.rpwt.wlwdw.data.model.Message
import org.rpwt.wlwdw.data.model.MessageDay

/**
 * The message store, as the UI consumes it.
 *
 * The screens never touch the database types: they get [Message], which already
 * has the display time and the day group worked out. That is what keeps the
 * storage shape free to change without every list item changing with it.
 */
class MessageRepository(context: Context) {

    private val dao = WlwdwDatabase.get(context).messages()

    /** Newest first, ready to group. */
    fun observe(): Flow<List<Message>> = dao.observeAll().map { rows -> rows.map(::toMessage) }

    suspend fun markAllRead() = dao.markAllRead()

    /**
     * Store a message pulled from the server.
     *
     * [receivedAt] is passed in rather than read here, so it is the caller's
     * clock that decides the day group and the ordering -- the two have to agree
     * with each other, and taking both from one instant is the way to guarantee
     * that.
     */
    suspend fun store(
        sender: String,
        content: String,
        serverTime: String,
        receivedAt: Long = System.currentTimeMillis(),
    ) {
        dao.insert(
            MessageEntity(
                time = serverTime,
                sender = sender,
                content = content,
                receivedAt = receivedAt,
            ),
        )
    }

    /**
     * Store a message that arrived as a push from the server.
     *
     * Its own method rather than a sender string at the call site: "was this the
     * server or a person" decides the colour of a list item, and the answer has
     * to be the same string everywhere or the colour stops meaning anything.
     */
    suspend fun storeFromServer(
        content: String,
        serverTime: String,
        receivedAt: Long = System.currentTimeMillis(),
    ) = store(sender = SERVER_SENDER, content = content, serverTime = serverTime, receivedAt = receivedAt)

    private fun toMessage(row: MessageEntity) = Message(
        id = row.id,
        sender = row.sender,
        body = row.content,
        time = row.time,
        unread = !row.read,
        fromServer = row.sender == SERVER_SENDER,
        day = dayOf(row.receivedAt),
    )

    private fun dayOf(receivedAt: Long): MessageDay {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when {
            // isAfter as well as equal: a device whose clock was corrected
            // backwards would otherwise file today's messages under "earlier".
            !day.isBefore(today) -> MessageDay.Today
            day == today.minusDays(1) -> MessageDay.Yesterday
            else -> MessageDay.Earlier
        }
    }

    private companion object {
        /**
         * The sender name the server's own notices are stored under -- the same
         * literal the pre-rewrite push handler wrote. Kept as one constant so
         * that "is this a server notice" has a single answer.
         */
        const val SERVER_SENDER = "定位服务器"
    }
}
