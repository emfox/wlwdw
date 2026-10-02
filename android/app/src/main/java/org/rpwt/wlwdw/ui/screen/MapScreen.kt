package org.rpwt.wlwdw.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.ui.component.StatusPill
import org.rpwt.wlwdw.ui.component.StatusTone
import org.rpwt.wlwdw.ui.demo.DemoData
import org.rpwt.wlwdw.ui.theme.statusColors

/**
 * Tab 2: the map.
 *
 * Full screen on purpose -- no top app bar. The pre-rewrite map kept an
 * ActionBar above it, which cost 56dp of usable height for nothing; the
 * tracking state that the bar was standing in for is a floating chip here.
 *
 * The map itself is a placeholder in this stage: a drawn grid with a fix dot
 * and two anchors, so the chrome around it (floating controls, the bottom
 * sheet) can be reviewed. The real tiles are the platform stage's job, and the
 * map remains a `View` wrapped in `AndroidView` when it arrives -- this is
 * interop, not a rewrite.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        MapBackdrop(Modifier.fillMaxSize())

        // The anchors of other devices and places, at loose fractions of the
        // viewport. They are drawn under the fix dot on purpose.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            AnchorDot(
                color = MaterialTheme.statusColors.warn,
                modifier = Modifier.offset(x = w * 0.22f, y = h * 0.24f),
            )
            AnchorDot(
                color = MaterialTheme.statusColors.ok,
                modifier = Modifier.offset(x = w * 0.72f, y = h * 0.52f),
            )
        }

        MyLocationDot(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-46).dp),
        )

        // Top centre: the tracking chip. It has to exist here because splitting
        // the app into tabs means the map can no longer see the status screen.
        StatusPill(
            text = stringResource(R.string.wlwdw_ring_reporting),
            tone = StatusTone.Ok,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        )

        MapFab(
            icon = Icons.Filled.MyLocation,
            contentDescription = stringResource(R.string.wlwdw_map_locate),
            onClick = {},
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 14.dp),
        )

        MapFab(
            icon = Icons.Outlined.Layers,
            contentDescription = stringResource(R.string.wlwdw_map_layers),
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = SheetHeightHint + 14.dp),
        )

        BottomSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Height the sheet roughly occupies, so the layer button can sit above it
 * without measuring. Only used for that offset.
 */
private val SheetHeightHint = 200.dp

/** The map itself, drawn: a grid, three roads and no labels yet. */
@Composable
private fun MapBackdrop(modifier: Modifier = Modifier) {
    val land = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val road = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        drawRect(color = land)

        val step = 34.dp.toPx()
        val hairline = 1.dp.toPx()
        var x = step
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = hairline)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = hairline)
            y += step
        }

        drawLine(
            road,
            Offset(0f, size.height * 0.34f),
            Offset(size.width, size.height * 0.34f),
            strokeWidth = 9.dp.toPx(),
        )
        drawLine(
            road,
            Offset(size.width * 0.31f, 0f),
            Offset(size.width * 0.31f, size.height),
            strokeWidth = 7.dp.toPx(),
        )
        drawLine(
            road,
            Offset(0f, size.height * 0.70f),
            Offset(size.width, size.height * 0.70f),
            strokeWidth = 6.dp.toPx(),
        )
    }
}

@Composable
private fun AnchorDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(13.dp)
            .background(color, CircleShape)
            .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}

@Composable
private fun MyLocationDot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(38.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape)
        )
        Box(
            Modifier
                .size(20.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

/** A floating control over the map: round, on the surface colour, lifted. */
@Composable
private fun MapFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(40.dp),
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * The sheet at the bottom: my location on top, then the other devices.
 *
 * It carries what the old ActionBar's "到当前定位点" item did, plus the anchors
 * that used to be invisible -- one place to see where everyone is.
 */
@Composable
private fun BottomSheet(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 34.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.wlwdw_map_my_location),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight(680),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = DemoData.mapFixAge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = DemoData.address,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            val anchorColors = listOf(
                MaterialTheme.statusColors.warn,
                MaterialTheme.statusColors.ok,
            )
            DemoData.anchors.forEachIndexed { index, anchor ->
                AnchorRow(
                    name = anchor.name,
                    time = anchor.time,
                    color = anchorColors[index % anchorColors.size],
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AnchorRow(name: String, time: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
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
    }
}
