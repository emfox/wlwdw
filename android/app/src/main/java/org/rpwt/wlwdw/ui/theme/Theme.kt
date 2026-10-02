package org.rpwt.wlwdw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Theme for the rewritten UI.
 *
 * Dynamic colour is deliberately not used. In this app colour carries meaning --
 * the four status colours are the app telling the user whether the device is
 * reporting -- and letting the wallpaper repaint them would break the rule the
 * design leans on hardest. Everything else still follows Material 3.
 */
@Composable
fun WlwdwTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WlwdwTypography,
            shapes = WlwdwShapes,
            content = content,
        )
    }
}

/**
 * The semantic status colours of the current theme, read as
 * `MaterialTheme.statusColors.ok` and friends.
 */
val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
