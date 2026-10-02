package org.rpwt.wlwdw.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The id carried by a push.
 *
 * The server publishes `{"id": <message row id>}` and nothing else: the text
 * stays on the server until the device asks for it, which is what keeps a push
 * small and lets the server check the device id before handing the body over.
 */
object PushPayload {

    /**
     * The message id in [payload], or null if there is not one.
     *
     * A bare id is accepted as well as the object, because that is what the
     * pre-rewrite handler did and nothing on the server side promises the
     * object form. What is *not* accepted is junk: the old code fell back to
     * using the whole payload as the id, which for any malformed push meant a
     * request to `/message/{"foo":1}/{devid}`.
     */
    fun messageId(payload: String): String? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null

        when (val element = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()) {
            is JsonObject -> {
                // A number or a string both read as a primitive, so the id is
                // taken as text either way and the server parses it back.
                val id = element["id"] as? JsonPrimitive ?: return null
                return id.content.takeIf { it.isNotEmpty() }
            }
            is JsonPrimitive -> return trimmed
            else -> return null
        }
    }
}
