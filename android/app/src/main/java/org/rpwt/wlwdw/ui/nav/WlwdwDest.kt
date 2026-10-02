package org.rpwt.wlwdw.ui.nav

import kotlinx.serialization.Serializable

/**
 * Every destination of the rewritten UI.
 *
 * These are navigation routes in the type-safe sense: the type itself is the
 * route, so a screen cannot be reached through a mistyped string and a removed
 * destination becomes a compile error rather than a runtime failure. Adding a
 * screen means adding a type here plus one `composable<...>` entry in
 * `WlwdwApp`.
 */
sealed interface WlwdwDest {

    /** Bottom bar tab 1: whether the device is reporting, and what the server last saw. */
    @Serializable
    data object Status : WlwdwDest

    /** Bottom bar tab 2: full-screen map. */
    @Serializable
    data object Map : WlwdwDest

    /** Bottom bar tab 3: received messages. */
    @Serializable
    data object Messages : WlwdwDest

    /**
     * Reached from the top bar, not a tab.
     *
     * The Material 3 navigation bar guidance allows 3-5 peer destinations and
     * rules out "accessing single tasks" and fewer than three entries, so
     * settings belong in the app bar rather than in the bar.
     */
    @Serializable
    data object Settings : WlwdwDest
}
