package org.rpwt.wlwdw.data.model

/**
 * One attempt at putting this device's position on the server, as it is kept.
 *
 * The app logs these rather than trusting the server to have them, because the
 * question that matters most on a bad day -- "when did it last work?" -- is
 * asked on a device that by definition could not reach the server. Keeping the
 * answer locally is also what lets the status screen say "last report 47
 * minutes ago" after a restart instead of claiming it has never reported.
 *
 * The coordinates are what was sent (WGS-84, the only system this app speaks)
 * and are absent when the attempt failed before there was anything to send.
 */
data class Report(
    /** When the attempt was made, on this device's clock. */
    val at: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMetres: Float? = null,
    /** `gps` / `network` / `passive`, as Android names them. */
    val provider: String? = null,
    /** Why nothing was stored; null means the server accepted the position. */
    val failure: ReportFailure? = null,
)
