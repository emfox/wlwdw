package org.rpwt.wlwdw.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.data.model.Report
import org.rpwt.wlwdw.data.model.ReportFailure
import org.rpwt.wlwdw.ui.component.HorizontalRule
import org.rpwt.wlwdw.ui.component.StatusPill
import org.rpwt.wlwdw.ui.component.StatusTone
import org.rpwt.wlwdw.ui.relativeTime
import org.rpwt.wlwdw.ui.theme.CoordinateTextStyle
import org.rpwt.wlwdw.ui.theme.WlwdwDimens

/**
 * The report log, newest first: what this device tried to send, and what came
 * of it.
 *
 * This is where the old location screen's four readouts went -- time, status,
 * GPS coordinates and accuracy -- plus the error the old screen showed as a bare
 * code. They were moved rather than deleted because they are the only view of
 * the thing that breaks: the loop's own state says what is happening now, and
 * this says what happened at 03:40 last night, when nobody was watching.
 *
 * The screen is read-only on purpose. Everything here is written by the
 * tracking loop, and a page that could edit its own log would not be evidence
 * of anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    reports: List<Report>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read once for the whole list rather than per row, so two rows never
    // measure their age against different "now"s and read as if written in the
    // wrong order.
    val now = System.currentTimeMillis()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.wlwdw_reports_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.wlwdw_action_back),
                    )
                }
            },
        )

        if (reports.isEmpty()) {
            EmptyReports(Modifier.fillMaxSize())
        } else {
            // The note is an item rather than a fixed header: on a phone it is
            // three lines of small print, and a header that cannot scroll would
            // spend a third of the screen saying it.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = WlwdwDimens.PagePadding,
                    end = WlwdwDimens.PagePadding,
                    top = WlwdwDimens.CardGap,
                    bottom = WlwdwDimens.CardGap,
                ),
            ) {
                item(key = "note") {
                    Text(
                        text = stringResource(R.string.wlwdw_reports_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                items(reports) { report ->
                    ReportRow(report, now)
                    HorizontalRule()
                }
            }
        }
    }
}

/**
 * One attempt.
 *
 * The time is the device's own wall clock to the second, not the relative
 * wording the product screens use: the question this page answers is "what
 * happened at 03:40", and a relative "7 hours ago" cannot be lined up against
 * anything outside the phone. The relative form stays beside it because it is what
 * says at a glance whether the newest row is current.
 */
@Composable
private fun ReportRow(report: Report, now: Long) {
    val failure = report.failure
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = wallClock(report.at),
                style = CoordinateTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = relativeTime(report.at, now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = failureText(failure), tone = toneOf(failure))
        }

        Text(
            text = report.position(),
            style = CoordinateTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )

        // The server's or the runtime's own words. Clamped: an exception's
        // message can be a paragraph, and the log is for scanning.
        failure?.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Where the fix was, in the same wording the status screen uses.
 *
 * Lateinit-free and null-safe: a failed attempt may have no fix at all, and
 * that is a different thing from a fix with no provider.
 */
@Composable
private fun Report.position(): String {
    if (latitude == null || longitude == null) {
        return stringResource(R.string.wlwdw_reports_no_position)
    }
    val fix = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    val accuracy = accuracyMetres?.let { String.format(Locale.US, "  ±%.0f m", it) }.orEmpty()
    val source = provider?.let { "  ${stringResource(fixSourceLabel(it))}" }.orEmpty()
    return fix + accuracy + source
}

@StringRes
private fun fixSourceLabel(provider: String): Int = when (provider) {
    "gps" -> R.string.wlwdw_fix_source_gps
    "network" -> R.string.wlwdw_fix_source_network
    else -> R.string.wlwdw_fix_source_other
}

/**
 * What became of the attempt.
 *
 * [ReportFailure.NoFix] and [ReportFailure.Permission] are both "there was no
 * position to send"; they stay distinct here because the fixes differ -- one is
 * a permission the user has to grant, the other is the phone not having a fix
 * yet.
 *
 * An unrecognised code is shown with the code itself rather than as a generic
 * failure: whoever reads this is the person who would have to recognise it, and
 * the stored string is the only clue the row carries.
 */
@Composable
private fun failureText(failure: ReportFailure?): String = when (failure) {
    null -> stringResource(R.string.wlwdw_reports_accepted)
    ReportFailure.Permission -> stringResource(R.string.wlwdw_reports_failed_permission)
    ReportFailure.NoFix -> stringResource(R.string.wlwdw_reports_failed_nofix)
    ReportFailure.Unregistered -> stringResource(R.string.wlwdw_reports_failed_unregistered)
    is ReportFailure.Refused -> stringResource(R.string.wlwdw_reports_failed_refused)
    is ReportFailure.Upload -> stringResource(R.string.wlwdw_reports_failed_upload)
    is ReportFailure.Unrecognized ->
        stringResource(R.string.wlwdw_reports_failed_unknown, failure.storedCode)
}

/**
 * How bad it was.
 *
 * Only the two failures that end this device's tracking on its own are errors:
 * a missing registration and a refusal are for a person to fix, whereas a
 * transient upload failure is what the next tick is for, and calling it an
 * error would make the log cry wolf every time the phone changes network.
 */
private fun toneOf(failure: ReportFailure?): StatusTone = when (failure) {
    null -> StatusTone.Ok
    ReportFailure.Unregistered, is ReportFailure.Refused -> StatusTone.Error
    else -> StatusTone.Warn
}

@Composable
private fun EmptyReports(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(WlwdwDimens.PagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Text(
            text = stringResource(R.string.wlwdw_reports_empty_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.wlwdw_reports_empty_body),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The moment, to the second, on the phone's own clock.
 *
 * `java.time` rather than a shared `SimpleDateFormat`, which is neither
 * immutable nor thread safe and would be a hazard the day something other than
 * composition formats a time. minSdk is 26, so this is in the platform.
 */
private val WallClock: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

private fun wallClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(WallClock)
