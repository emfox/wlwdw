package org.rpwt.wlwdw.data.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The body `/trail/new` expects. Field names are the server's, not ours. */
@Serializable
private data class TrailReport(val devid: String, val lat: Double, val lng: Double)

/**
 * The envelope every wlwdw endpoint answers with.
 *
 * One type for all of them, with defaults for whatever a given endpoint leaves
 * out: `result` appears only on the "deny" reply and `message` only on the
 * error ones, so both are nullable rather than the reply being split into
 * several classes that would each have to be kept in step with the server.
 */
@Serializable
data class ServerReply(
    val code: Int = 0,
    val success: Boolean = false,
    val result: String? = null,
    val message: String? = null,
)

/**
 * The envelope `/message/{id}/{devid}` answers with.
 *
 * Separate from [ServerReply] because its `message` is an object (`time` plus
 * `content`) and not the error string the other endpoints put there. The
 * pre-rewrite handler read it with `JSONObject.getString`, which cannot read an
 * object, so every push it pulled ended in its own catch block and nothing was
 * ever shown.
 */
@Serializable
data class MessageReply(
    val code: Int = 0,
    val success: Boolean = false,
    val message: MessageBody? = null,
)

@Serializable
data class MessageBody(val time: String = "", val content: String = "")

/**
 * A message body the server handed over.
 *
 * Not a row and not the [org.rpwt.wlwdw.data.model.Message] a list item shows:
 * the id came from the push and the arrival time belongs to this device, so
 * neither is something the server's answer contains.
 */
data class PulledMessage(val serverTime: String, val content: String)

/**
 * What became of one report.
 *
 * The four cases are the four different things the app should *do* next, which
 * is why this is not just an error string: [DeviceUnknown] must not be retried
 * (the server will never accept this device id, so retrying every two minutes
 * forever is pure noise), while [Unreachable] must be. Collapsing them would
 * make "no signal" and "you are not registered" look the same on screen.
 */
sealed interface ReportOutcome {

    /** Stored; the server now shows this device at these coordinates. */
    data object Accepted : ReportOutcome

    /**
     * The server does not know this device id (`{"result":"deny"}`).
     *
     * Note the server answers this with HTTP 200 on purpose, so that the
     * endpoint cannot be used to probe which device ids exist. That is why the
     * status code is not what decides this case.
     */
    data object DeviceUnknown : ReportOutcome

    /** The server rejected the request itself: our coordinates or body are wrong. */
    data class Rejected(val message: String?) : ReportOutcome

    /** Nothing was stored anywhere: DNS, TLS, timeout, no network. Worth retrying. */
    data class Unreachable(val reason: String) : ReportOutcome
}

/**
 * The wlwdw HTTP API, as far as the device uses it.
 *
 * The one implementation of [ReportSender] that is not a test double. The
 * client is injected so a test can hand in Ktor's MockEngine and assert on the
 * exact request; the default is the real one.
 */
class WlwdwApi(private val client: HttpClient = defaultClient()) : ReportSender {

    /**
     * [host] is the `host[:port]` from the settings, and may carry a scheme:
     * the pre-rewrite app hardcoded `https://`, which left a self-hosted server
     * on plain HTTP unreachable with no way for the user to say so.
     */
    override suspend fun report(host: String, devid: String, lat: Double, lng: Double): ReportOutcome {
        val url = "${baseUrl(host)}/trail/new"
        return try {
            val reply: ServerReply = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(TrailReport(devid, lat, lng))
            }.body()

            when {
                reply.result == "deny" -> ReportOutcome.DeviceUnknown
                reply.success && reply.code == 100 -> ReportOutcome.Accepted
                else -> ReportOutcome.Rejected(reply.message)
            }
        } catch (e: CancellationException) {
            // The caller cancelling (tracking stopped) is not a network fault.
            throw e
        } catch (e: Exception) {
            ReportOutcome.Unreachable(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /**
     * Pull one message body.
     *
     * A push carries only `{"id": ...}`: the text lives on the server, which
     * checks the devid before handing it over. A missing message, one addressed
     * to another device, and an unreadable answer are all the same thing to the
     * caller -- there is nothing to show -- so they share one answer, null.
     * (The first two reply with `message` as a string, which does not fit
     * [MessageReply] and so lands in the catch below. The outcome is the same.)
     */
    suspend fun pullMessage(host: String, devid: String, id: String): PulledMessage? {
        val url = "${baseUrl(host)}/message/$id/$devid"
        return try {
            val reply: MessageReply = client.get(url).body()
            if (reply.success && reply.message != null) {
                PulledMessage(reply.message.time, reply.message.content)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun baseUrl(host: String): String {
        val trimmed = host.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}

/**
 * The one client for the process.
 *
 * Timeouts are set explicitly: the pre-rewrite code had none, so a report to an
 * unreachable host could sit there until the socket gave up, which in practice
 * meant the next report never happened.
 */
private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
    // A non-2xx is a valid answer here -- the server uses the body, not the
    // status, to say what happened -- so let the caller parse it rather than
    // turning it into an exception.
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 20_000
        socketTimeoutMillis = 20_000
    }
}
