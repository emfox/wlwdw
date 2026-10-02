package org.rpwt.wlwdw.data

import org.rpwt.wlwdw.data.model.Report

/**
 * The report log, as the tracking loop uses it.
 *
 * An interface rather than [ReportRepository] itself so the loop can be tested
 * without Room: the loop's decisions are about what the server said, and a test
 * that has to stand up a database before it can ask "does a deny stop the loop"
 * is testing the wrong thing.
 */
interface ReportLog {

    /**
     * Store one attempt.
     *
     * Expected to be best effort: this is diagnostics, and a device whose
     * storage is full must not stop reporting because the row about the report
     * could not be written.
     */
    suspend fun record(report: Report)

    /**
     * When the last report was accepted, or null if none ever was.
     *
     * Never reporting is a real answer and is kept distinct from "not read
     * yet": the status screen says "还没有成功上报" for one and "last success 47
     * minutes ago" for the other.
     */
    suspend fun lastAcceptedAt(): Long?
}
