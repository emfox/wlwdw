package org.rpwt.wlwdw.push

import org.rpwt.wlwdw.data.prefs.WlwdwPrefs

/**
 * The MQTT broker a device connects to.
 *
 * Every default here is the value the pre-rewrite `MqttConfig` used, carried
 * over unchanged: the port is always 443, the WebSocket path always `/mqtt`,
 * and TLS is always on. Devices reach the self-hosted EMQX broker through the
 * same public hostname the HTTP calls use, where the front proxy routes the
 * `/mqtt` path to it -- which is why only the host is configurable and the rest
 * is not.
 *
 * TLS can be turned off so a debug build can talk to a broker on the host; no
 * build does that today, and the field exists rather than a second constructor
 * because "same host, no certificate" is the whole difference.
 */
data class BrokerEndpoint(
    val host: String,
    val port: Int = DEFAULT_PORT,
    val path: String = DEFAULT_PATH,
    val tls: Boolean = true,
) {
    companion object {
        const val DEFAULT_PORT = 443
        const val DEFAULT_PATH = "/mqtt"

        /**
         * The broadcast topic every device subscribes to, so the server can
         * address all of them at once. Alongside it, each device subscribes to
         * the topic named after its own device id.
         */
        const val TOPIC_ALL = "all"
    }
}

/** The broker this install talks to: the same host the HTTP calls use. */
fun brokerEndpoint(prefs: WlwdwPrefs): BrokerEndpoint = BrokerEndpoint(host = prefs.serverHost)
