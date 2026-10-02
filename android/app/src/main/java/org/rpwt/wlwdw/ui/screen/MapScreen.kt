package org.rpwt.wlwdw.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Tab 2: the map.
 *
 * Full screen on purpose -- no top app bar. The pre-rewrite map screen kept an
 * ActionBar above it, which costs 56dp of usable height on a phone for nothing.
 * When this is built out, the tracking status that the bar used to be a proxy
 * for goes into a floating chip at the top instead, and the controls float over
 * the map.
 */
@Composable
fun MapScreen() {
    ScreenPlaceholder(modifier = Modifier.fillMaxSize())
}
