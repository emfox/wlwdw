package org.rpwt.wlwdw.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Design tokens from the UI design, section 05. Only the values the design
 * pins down live here; everything else stays on the Material 3 defaults.
 *
 * Two rules from the design shape this file:
 *
 *  1. Colour expresses state and nothing else, and at most two semantic colours
 *     are on screen at the same time -- which is why the brand blue is a
 *     separate series from the four status colours below.
 *  2. Material 3 has no role for "ok" / "warn" / "idle". Rather than abuse a
 *     role that means something else, they travel in their own
 *     [StatusColors] CompositionLocal.
 */

// --- Brand -------------------------------------------------------------------
private val Brand = Color(0xFF1B6EF3)
private val BrandInk = Color(0xFF0B4FC4)
private val BrandSoft = Color(0xFFE8F0FF)
private val BrandOnDark = Color(0xFF6BA1FF)
private val BrandInkOnDark = Color(0xFFA8C6FF)
private val BrandSoftOnDark = Color(0xFF15243C)

// --- Neutrals, light ---------------------------------------------------------
private val PageLight = Color(0xFFF6F7F9)
private val SurfaceLight = Color(0xFFFFFFFF)
private val InkLight = Color(0xFF14161A)
private val Ink2Light = Color(0xFF5B6472)
private val LineLight = Color(0xFFE3E7EE)
private val Line2Light = Color(0xFFEEF1F6)

// --- Neutrals, dark ----------------------------------------------------------
private val PageDark = Color(0xFF111318)
private val SurfaceDark = Color(0xFF171A20)
private val InkDark = Color(0xFFEEF1F6)
private val Ink2Dark = Color(0xFFA2ACBB)
private val LineDark = Color(0xFF2A2F38)
private val Line2Dark = Color(0xFF22262E)

// --- Status, light -----------------------------------------------------------
private val OkLight = Color(0xFF12A150)
private val OkSoftLight = Color(0xFFE6F6EC)
private val WarnLight = Color(0xFFC47F0A)
private val WarnSoftLight = Color(0xFFFDF3E0)
private val ErrorLight = Color(0xFFD93A3F)
private val ErrorSoftLight = Color(0xFFFDECEC)
private val IdleLight = Color(0xFF8B95A5)

// --- Status, dark ------------------------------------------------------------
// The design gives the dark variant of "ok" only (#3DD07A). The other two are
// their light values lifted in luminance so they stay readable on #111318 --
// these are the one group of values here that is not literally in the design,
// and they are the ones to revisit first if the dark theme looks off.
private val OkDark = Color(0xFF3DD07A)
private val OkSoftDark = Color(0xFF12281C)
private val WarnDark = Color(0xFFE0A33C)
private val WarnSoftDark = Color(0xFF2B2312)
private val ErrorDark = Color(0xFFF26B6F)
private val ErrorSoftDark = Color(0xFF2C1618)
private val IdleDark = Color(0xFF8B95A5)

/**
 * The semantic status colours: what the app is telling the user about the
 * device, as opposed to what it is made of.
 *
 * Each colour has a matching container (the soft, filled variant used behind
 * text or as a chip background).
 */
@Immutable
data class StatusColors(
    val ok: Color,
    val okContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val idle: Color,
)

internal val LightStatusColors = StatusColors(
    ok = OkLight,
    okContainer = OkSoftLight,
    warn = WarnLight,
    warnContainer = WarnSoftLight,
    error = ErrorLight,
    errorContainer = ErrorSoftLight,
    idle = IdleLight,
)

internal val DarkStatusColors = StatusColors(
    ok = OkDark,
    okContainer = OkSoftDark,
    warn = WarnDark,
    warnContainer = WarnSoftDark,
    error = ErrorDark,
    errorContainer = ErrorSoftDark,
    idle = IdleDark,
)

internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/**
 * The Material 3 colour scheme.
 *
 * The bottom navigation bar reads three roles from here -- container from
 * `surfaceContainer`, active label from `secondary`, active indicator from
 * `secondaryContainer` -- so those three are what make the bar match the
 * design. The rest follow from the neutrals above.
 */
internal val LightColorScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandSoft,
    onPrimaryContainer = BrandInk,
    secondary = Brand,
    onSecondary = Color.White,
    secondaryContainer = BrandSoft,
    onSecondaryContainer = BrandInk,
    tertiary = Brand,
    onTertiary = Color.White,
    background = PageLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Line2Light,
    onSurfaceVariant = Ink2Light,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = PageLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = Line2Light,
    surfaceContainerHighest = Line2Light,
    outline = LineLight,
    outlineVariant = Line2Light,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorSoftLight,
    onErrorContainer = ErrorLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = BrandOnDark,
    onPrimary = Color(0xFF00295E),
    primaryContainer = BrandSoftOnDark,
    onPrimaryContainer = BrandInkOnDark,
    secondary = BrandOnDark,
    onSecondary = Color(0xFF00295E),
    secondaryContainer = BrandSoftOnDark,
    onSecondaryContainer = BrandInkOnDark,
    tertiary = BrandOnDark,
    onTertiary = Color(0xFF00295E),
    background = PageDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Line2Dark,
    onSurfaceVariant = Ink2Dark,
    surfaceContainerLowest = PageDark,
    surfaceContainerLow = PageDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = Line2Dark,
    surfaceContainerHighest = LineDark,
    outline = LineDark,
    outlineVariant = Line2Dark,
    error = ErrorDark,
    onError = Color(0xFF3A0A0C),
    errorContainer = ErrorSoftDark,
    onErrorContainer = ErrorDark,
)
