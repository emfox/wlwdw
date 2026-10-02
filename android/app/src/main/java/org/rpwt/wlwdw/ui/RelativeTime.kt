package org.rpwt.wlwdw.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.rpwt.wlwdw.R

/**
 * "3 分钟前" for a timestamp, coarse on purpose.
 *
 * The screen answers "can my family see me", and that question is answered by
 * the order of magnitude -- seconds of precision would imply a freshness this
 * data does not have, since the report interval is minutes.
 *
 * [now] is a parameter so the caller decides what "now" is: recomposing on a
 * clock read would tick every value on screen, and the state changes when a
 * report happens anyway.
 */
@Composable
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    // Negative when the device clock was corrected backwards; "just now" is the
    // honest answer for a timestamp from the future.
    val minutes = ((now - millis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> stringResource(R.string.wlwdw_ago_now)
        minutes < 60 -> stringResource(R.string.wlwdw_ago_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.wlwdw_ago_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.wlwdw_ago_days, (minutes / (60 * 24)).toInt())
    }
}
