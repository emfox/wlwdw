package org.rpwt.wlwdw.data.model

/**
 * Why an attempt to report this device's position did not put it on the server.
 *
 * These live here rather than next to the loop that produces them because they
 * are not a detail of that loop: the status screen reads them to decide what to
 * tell the user, and the report log stores them. The distinction that matters is
 * which ones are worth retrying -- see [Report] and the loop in `tracking`.
 */
sealed interface ReportFailure {

    /** Location is not granted, so there is nothing to report. */
    data object Permission : ReportFailure

    /** Granted, but no provider produced a position. */
    data object NoFix : ReportFailure

    /**
     * The server does not know this device id (`{"result":"deny"}`).
     *
     * A case of its own because it is the one failure retrying cannot fix: the
     * device has to be registered server-side. Everything else here is either
     * transient ([Upload]) or about this device's configuration.
     */
    data object Unregistered : ReportFailure

    /** The server rejected the request itself: our coordinates or body are wrong. */
    data class Refused(val message: String?) : ReportFailure

    /** The upload did not get through. Retried on the next tick. */
    data class Upload(val reason: String) : ReportFailure

    /**
     * How this failure is written into the report log.
     *
     * Stable strings rather than `enumValueOf<ReportFailure>()`: they are what
     * is already on disk, so renaming a Kotlin case must not silently orphan
     * the rows written before the rename.
     */
    val code: String
        get() = when (this) {
            Permission -> "permission"
            NoFix -> "nofix"
            Unregistered -> "unregistered"
            is Refused -> "refused"
            is Upload -> "upload"
        }

    /**
     * The text behind the failure, for the two cases that carry one.
     *
     * Kept beside [code] so the stored row is one write and not a mapping
     * scattered over the callers.
     */
    val detail: String?
        get() = when (this) {
            is Refused -> message
            is Upload -> reason
            else -> null
        }
}
