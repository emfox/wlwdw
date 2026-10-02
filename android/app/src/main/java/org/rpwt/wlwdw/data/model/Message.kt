package org.rpwt.wlwdw.data.model

/**
 * A received message, as the screens read it.
 *
 * Separate from the stored row on purpose: the row is what the database needs
 * (a server timestamp string, an arrival instant), while this is what a list
 * item needs (a display time, which day it belongs to, whether the sender was
 * the server). Keeping the two apart is what stops a storage detail from
 * leaking into the UI.
 */
data class Message(
    val id: Long,
    val sender: String,
    val body: String,
    /** Display text, exactly as the server wrote it. */
    val time: String,
    val unread: Boolean,
    /** True for server notices, which get the brand colour instead of an avatar. */
    val fromServer: Boolean,
    val day: MessageDay,
)

/** Which group a message is listed under. */
enum class MessageDay { Today, Yesterday, Earlier }
