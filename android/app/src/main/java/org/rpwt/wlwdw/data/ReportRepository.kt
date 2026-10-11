package org.rpwt.wlwdw.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.rpwt.wlwdw.data.db.ReportEntity
import org.rpwt.wlwdw.data.db.WlwdwDatabase
import org.rpwt.wlwdw.data.model.Report
import org.rpwt.wlwdw.data.model.ReportFailure

/**
 * The report log, backed by the `report` table.
 *
 * The one implementation of [ReportLog] that is not a test double. It exists to
 * keep Room out of the tracking loop: the loop hands over a [Report] and never
 * learns that there is a `report` table, which is what would otherwise be the
 * first crack in the layering.
 */
class ReportRepository(context: Context) : ReportLog {

    private val dao = WlwdwDatabase.get(context).reports()

    /**
     * Store one attempt, then drop whatever is past the cap.
     *
     * Pruning here rather than on a schedule: writes are minutes apart, so the
     * cost is irrelevant, and a log that is only trimmed by some other job is a
     * log that grows without bound the day that job is removed.
     *
     * Best effort on purpose, as [ReportLog] asks for. This is diagnostics; the
     * tracking loop is the product, and a device whose storage is full must not
     * stop reporting because a row about the report could not be written. The
     * failure is therefore dropped rather than thrown -- the alternative is a
     * tracker that goes quiet for a reason nobody can see on screen.
     */
    override suspend fun record(report: Report) {
        runCatching {
            dao.insert(
                ReportEntity(
                    at = report.at,
                    latitude = report.latitude,
                    longitude = report.longitude,
                    accuracy = report.accuracyMetres,
                    provider = report.provider,
                    failure = report.failure?.code,
                    detail = report.failure?.detail,
                ),
            )
            dao.prune(MAX_ROWS)
        }
    }

    /**
     * This is what the status screen starts from, so that "last report 47
     * minutes ago" survives a restart of the app.
     */
    override suspend fun lastAcceptedAt(): Long? = dao.lastAcceptedAt()

    /**
     * The log, newest first, as the diagnostics page reads it.
     *
     * Not part of [ReportLog]: the loop only ever writes and asks when it last
     * worked, and a reader on that interface would be a reader the loop does
     * not need. Living here instead of on the entity also keeps the screen out
     * of how a failure was flattened to fit in a column -- it gets a [Report]
     * back, which is what it would have got had it been there when the attempt
     * happened.
     */
    fun observeAll(): Flow<List<Report>> = dao.observeAll().map { rows -> rows.map { it.toReport() } }

    /**
     * The inverse of [record]'s flattening.
     *
     * A stored row is the only thing that survives a restart, so a failure is
     * rebuilt rather than dropped: [ReportFailure.of] keeps a code this build
     * does not recognise instead of returning null, because null on this type
     * means "the server accepted the position".
     */
    private fun ReportEntity.toReport(): Report = Report(
        at = at,
        latitude = latitude,
        longitude = longitude,
        accuracyMetres = accuracy,
        provider = provider,
        failure = failure?.let { ReportFailure.of(it, detail) },
    )

    private companion object {
        /**
         * About a day at the shortest interval the settings offer (1 minute)
         * and two days at the default. Long enough for "what happened
         * overnight", short enough that the table stays a few tens of KB.
         */
        const val MAX_ROWS = 1440
    }
}
