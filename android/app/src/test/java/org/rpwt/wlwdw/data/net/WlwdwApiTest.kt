package org.rpwt.wlwdw.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs

/**
 * The contract between this app and the wlwdw server.
 *
 * These are not tests of Ktor. They are the facts about the server that the
 * pre-rewrite client got wrong and that nothing on screen would reveal: an
 * unknown device arrives as a success status code, the host may be plain HTTP,
 * the body has exactly three fields with the server's names, and a reply that
 * grows a field must not stop the app from parsing it.
 */
class WlwdwApiTest {

    @Test
    fun `a success reply reads as accepted`() = runTest {
        val server = MockServer(body = """{"code":100,"success":true}""")

        assertEquals(ReportOutcome.Accepted, server.api().report(HOST, DEVICE_ID, LAT, LNG))
    }

    /**
     * The body decides, not the status code.
     *
     * This is the shape the server really answers with: HTTP 200 plus
     * `{"result":"deny"}`, on purpose, so that `/trail/new` cannot be used to
     * find out which device ids exist. Reading the status would file this as a
     * success. The second pass pins the other half -- the same body on a
     * failing status is still an unknown device, because it is the body that
     * decides.
     */
    @Test
    fun `an unknown device is the body saying deny, not a failing status`() = runTest {
        val deny = """{"result":"deny","code":403,"message":"设备不存在"}"""

        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.Forbidden)) {
            val server = MockServer(status = status, body = deny)

            assertEquals(
                "with status $status",
                ReportOutcome.DeviceUnknown,
                server.api().report(HOST, DEVICE_ID, LAT, LNG),
            )
        }
    }

    @Test
    fun `a request is a JSON POST of exactly devid, lat and lng`() = runTest {
        val server = MockServer(body = """{"code":100,"success":true}""")

        server.api().report(HOST, DEVICE_ID, LAT, LNG)

        assertEquals(HttpMethod.Post, server.last.method)
        assertEquals("https://$HOST/trail/new", server.last.url.toString())
        // Parameters dropped on purpose: a charset on a UTF-8-only body is not
        // something this app has an opinion about.
        assertEquals(ContentType.Application.Json, server.lastBodyType?.withoutParameters())
        assertEquals(
            """{"devid":"$DEVICE_ID","lat":$LAT,"lng":$LNG}""",
            server.lastBody,
        )
    }

    @Test
    fun `a host may carry a scheme, and gets https when it does not`() = runTest {
        val server = MockServer(body = """{"code":100,"success":true}""")
        val api = server.api()

        api.report("example.org", DEVICE_ID, LAT, LNG)
        assertEquals("https://example.org/trail/new", server.last.url.toString())

        // A self-hosted server on plain HTTP, which the pre-rewrite client
        // could not reach at all: it hardcoded the scheme, and the trailing
        // slash would have survived into the path.
        api.report("http://10.0.2.2:8080/", DEVICE_ID, LAT, LNG)
        assertEquals("http://10.0.2.2:8080/trail/new", server.last.url.toString())

        // And the built-in server, which is where a fresh install reports.
        val fresh = WlwdwPrefs(
            deviceId = DEVICE_ID,
            consentAccepted = true,
            useCustomHost = false,
            customHost = "",
            reportIntervalMinutes = 2,
        )
        api.report(fresh.serverHost, DEVICE_ID, LAT, LNG)
        assertEquals("https://${WlwdwPrefs.DEFAULT_HOST}/trail/new", server.last.url.toString())
    }

    @Test
    fun `a rejected request is a refusal carrying the server's own text`() = runTest {
        val server = MockServer(
            status = HttpStatusCode.InternalServerError,
            body = """{"success":false,"code":500,"message":"坐标超出范围"}""",
        )

        assertEquals(
            ReportOutcome.Rejected("坐标超出范围"),
            server.api().report(HOST, DEVICE_ID, LAT, LNG),
        )
    }

    @Test
    fun `a reply that grows a field still parses`() = runTest {
        val server = MockServer(
            body = """{"code":100,"success":true,"result":"ok","serverTime":1759400000}""",
        )

        assertEquals(ReportOutcome.Accepted, server.api().report(HOST, DEVICE_ID, LAT, LNG))
    }

    @Test
    fun `a connection that never happened is unreachable, not a refusal`() = runTest {
        val server = MockServer(fail = { throw IOException("Connection refused") })

        assertEquals(
            ReportOutcome.Unreachable("Connection refused"),
            server.api().report(HOST, DEVICE_ID, LAT, LNG),
        )
    }

    /**
     * Cancelling a report (the user stopped tracking) must not be filed as a
     * failed upload: the loop is being torn down, and a row blaming the network
     * would be a lie about this device's connectivity.
     */
    @Test
    fun `cancellation is rethrown rather than read as an upload failure`() = runTest {
        val server = MockServer(fail = { throw CancellationException("tracking stopped") })

        val thrown = runCatching { server.api().report(HOST, DEVICE_ID, LAT, LNG) }.exceptionOrNull()

        assertTrue("expected a cancellation, got $thrown", thrown is CancellationException)
    }

    /**
     * A push carries only an id, and the body is fetched with this device's own
     * id in the path -- which is what stops one device from reading another's
     * mail should a push ever be misdelivered.
     */
    @Test
    fun `a message is pulled with the device id in the path`() = runTest {
        val server = MockServer(
            body = """{"code":100,"success":true,"message":{"time":"2026-10-02 18:04:31","content":"到家了没"}}""",
        )

        val pulled = server.api().pullMessage(HOST, DEVICE_ID, "42")

        assertEquals(PulledMessage("2026-10-02 18:04:31", "到家了没"), pulled)
        assertEquals(HttpMethod.Get, server.last.method)
        assertEquals("https://$HOST/message/42/$DEVICE_ID", server.last.url.toString())
    }

    /**
     * `message` is an object, not a string.
     *
     * That is the shape the server really sends, and it is the one thing the
     * pre-rewrite handler could not read: it called `JSONObject.getString`,
     * which throws on an object, so every push it received ended in its catch
     * block and no message was ever shown.
     */
    @Test
    fun `a message this device cannot have is not a message`() = runTest {
        // Gone from the server: a real 404.
        val missing = MockServer(
            status = HttpStatusCode.NotFound,
            body = """{"code":404,"success":false,"message":"Message not found"}""",
        )
        assertNull(missing.api().pullMessage(HOST, DEVICE_ID, "42"))

        // Addressed to another device: a real 200, but not ours.
        val foreign = MockServer(
            body = """{"code":403,"success":false,"message":"Device Unauthorized"}""",
        )
        assertNull(foreign.api().pullMessage(HOST, DEVICE_ID, "42"))
    }

    /** Nothing to show is an answer, not an exception to handle at the call site. */
    @Test
    fun `a pull that never connected is null rather than a throw`() = runTest {
        val server = MockServer(fail = { throw IOException("Connection refused") })

        assertNull(server.api().pullMessage(HOST, DEVICE_ID, "42"))
    }

    private companion object {
        const val DEVICE_ID = "b14fcbbb-d2c3-48fd-b72d-513689310f42"
        const val HOST = "example.org:8080"
        const val LAT = 30.2741
        const val LNG = 120.12625
    }
}

/**
 * A `WlwdwApi` pointed at a canned server, keeping every request it received.
 *
 * The client is configured exactly like the real one where that matters: a
 * non-2xx must arrive as a response rather than as an exception, and unknown
 * keys must be ignored. A mock that skipped those would be testing a client the
 * app does not ship.
 */
private class MockServer(
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val body: String = "",
    private val fail: (() -> Nothing)? = null,
) {

    private val requests = mutableListOf<HttpRequestData>()

    fun api(): WlwdwApi {
        val engine = MockEngine { request ->
            requests += request
            fail?.invoke()
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return WlwdwApi(
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
        )
    }

    val last: HttpRequestData get() = requests.last()

    val lastBodyType: ContentType?
        get() = (last.body as? TextContent)?.contentType

    val lastBody: String
        get() = when (val sent = last.body) {
            is TextContent -> sent.text
            is OutgoingContent.ByteArrayContent -> sent.bytes().decodeToString()
            else -> throw AssertionError("unexpected body type ${sent::class.qualifiedName}")
        }
}
