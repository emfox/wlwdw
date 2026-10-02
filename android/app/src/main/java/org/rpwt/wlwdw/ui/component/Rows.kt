package org.rpwt.wlwdw.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rpwt.wlwdw.ui.theme.CoordinateTextStyle

/**
 * A sender's initial on a coloured disc: the design's stand-in for an avatar,
 * since the app has no contact photos and must not ask for them.
 *
 * Server notices get the brand colour so they read as a different kind of
 * message from a person's.
 */
@Composable
fun SenderAvatar(
    name: String,
    fromServer: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (fromServer) MaterialTheme.colorScheme.primary else avatarColorFor(name)
    Box(
        modifier = modifier.size(22.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight(680),
        )
    }
}

private val AvatarPalette = listOf(
    Color(0xFFE8734A),
    Color(0xFF7A5AF8),
    Color(0xFF12A150),
    Color(0xFFC47F0A),
)

private fun avatarColorFor(name: String): Color {
    val index = (name.hashCode() % AvatarPalette.size + AvatarPalette.size) % AvatarPalette.size
    return AvatarPalette[index]
}

/**
 * One received message. Unread ones get the brand container as their
 * background and a dot by the timestamp, which is what the navigation bar's
 * badge counts.
 */
@Composable
fun MessageRow(
    sender: String,
    body: String,
    time: String,
    unread: Boolean,
    fromServer: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (unread) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SenderAvatar(name = sender, fromServer = fromServer)
                Text(
                    text = sender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight(660),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unread) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (unread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * One row of the settings list: a label, an optional explanation and value, and
 * whatever control belongs on the right.
 *
 * The value can be rendered monospaced for things that are codes rather than
 * words (device id, host).
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    monospaceValue: Boolean = false,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 2.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.45f
                        ),
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = if (monospaceValue) {
                        CoordinateTextStyle
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.45f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalRule()
        }
    }
}

/** The hairline between rows when the divider is not drawn by the row itself. */
@Composable
internal fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
