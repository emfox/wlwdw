package org.rpwt.wlwdw.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What counts as a message id in a push.
 *
 * Worth pinning because the server publishes `{"id": ...}` but the old handler
 * also accepted a bare id, and because its fallback for anything it could not
 * parse was to use the whole payload as the id -- which sent requests to
 * `/message/{"foo":1}/{devid}` rather than dropping the push.
 */
class PushPayloadTest {

    @Test
    fun `an id in a JSON object is read as text`() {
        assertEquals("42", PushPayload.messageId("""{"id":42}"""))
        assertEquals("42", PushPayload.messageId("""{"id":"42"}"""))
    }

    @Test
    fun `a bare id is accepted too`() {
        assertEquals("42", PushPayload.messageId("42"))
        assertEquals("42", PushPayload.messageId("  42\n"))
    }

    @Test
    fun `nothing in, no id out`() {
        assertNull(PushPayload.messageId(""))
        assertNull(PushPayload.messageId("   "))
    }

    @Test
    fun `a push with no id is dropped rather than used as one`() {
        assertNull(PushPayload.messageId("""{"foo":1}"""))
        assertNull(PushPayload.messageId("""{"id":""}"""))
    }

    @Test
    fun `a push that is not JSON is dropped rather than used as one`() {
        assertNull(PushPayload.messageId("""{"id":"""))
    }
}
