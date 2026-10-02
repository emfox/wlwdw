package org.rpwt.wlwdw.data.net

/**
 * One report, sent.
 *
 * The tracking loop depends on this rather than on [WlwdwApi] so that a test can
 * say "the server answers deny" without an HTTP client standing in the way. The
 * single method returns a [ReportOutcome] rather than throwing, which is what
 * makes those tests readable: "not registered" and "no signal" are two answers,
 * not an answer and an exception.
 */
interface ReportSender {

    /**
     * Report one fix.
     *
     * [host] is the `host[:port]` from the settings and may carry a scheme;
     * see [WlwdwApi.report] for why that is not narrowed here.
     */
    suspend fun report(host: String, devid: String, lat: Double, lng: Double): ReportOutcome
}
