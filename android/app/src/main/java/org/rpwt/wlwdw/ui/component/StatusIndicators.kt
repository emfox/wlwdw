package org.rpwt.wlwdw.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rpwt.wlwdw.ui.theme.statusColors

/**
 * How a piece of status is coloured.
 *
 * The design reserves colour for meaning and allows at most two of these on
 * screen at once, so this is deliberately a tiny closed set rather than a
 * free colour parameter.
 */
enum class StatusTone { Ok, Warn, Error, Idle }

/** Resolve a tone into its foreground colour and its matching container. */
@Composable
internal fun toneColors(tone: StatusTone): Pair<Color, Color> {
    val status = MaterialTheme.statusColors
    return when (tone) {
        StatusTone.Ok -> status.ok to status.okContainer
        StatusTone.Warn -> status.warn to status.warnContainer
        StatusTone.Error -> status.error to status.errorContainer
        StatusTone.Idle -> status.idle to MaterialTheme.colorScheme.surfaceVariant
    }
}

private val RingSize = 132.dp
private val RingStroke = 7.dp

/**
 * The ring at the top of the status screen: the one thing the user reads
 * without reading. A coloured arc means the device is reporting; a grey one
 * means it is not.
 */
@Composable
fun StatusRing(
    tone: StatusTone,
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    progress: Float = 0.78f,
) {
    val (color, container) = toneColors(tone)
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(RingSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // A dot with a halo, so the state also reads as a colour at a glance.
            Box(
                modifier = Modifier.size(21.dp).background(container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(11.dp).background(color, CircleShape))
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A small pill carrying one status word ("正常", "重连中"). Also used for the
 * floating tracking chip on the map and in the messages bar -- same shape and
 * size, different place.
 */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    withDot: Boolean = true,
) {
    val (color, container) = toneColors(tone)
    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(percent = 50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (withDot) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight(620),
        )
    }
}
