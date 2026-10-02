package org.rpwt.wlwdw.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The design's type scale, mapped onto the Material 3 slots the screens use.
 * The design names the steps bodyStrong / title / headline / body / caption;
 * the slot each one lands in is noted in the comment so the two can be kept in
 * step if either side moves.
 *
 * The weights are the literal design values (620 / 680). Compose accepts any
 * weight in 1..1000 and the platform picks the closest face it has, so these
 * degrade gracefully on fonts without that exact weight.
 */
val WlwdwTypography = Typography(
    // headline -- first-run title
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight(700),
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    // title -- screen title
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight(680),
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    // bodyStrong -- address / card headline
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight(620),
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    // body -- status line
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight(400),
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    // caption -- secondary explanation
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight(400),
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    ),
)

/**
 * Coordinates are always monospace and never grouped with thousand separators:
 * a coordinate is a label, not a quantity. Not a Material slot -- use it
 * directly where a coordinate is rendered.
 */
val CoordinateTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight(500),
    fontSize = 11.5.sp,
    lineHeight = 16.sp,
)
