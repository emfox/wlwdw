package org.rpwt.wlwdw.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing tokens.
 *
 * The design works on an 8pt grid. Two values recur everywhere and are worth
 * naming; the rest of the grid is written inline where it is used.
 */
object WlwdwDimens {

    /** Horizontal page margin. */
    val PagePadding = 18.dp

    /** Gap between stacked cards. */
    val CardGap = 10.dp

    /**
     * M3 Expressive short navigation bar height. The baseline navigation bar
     * this replaced was 80dp; the component owns the real height, this is here
     * so layouts can reserve room for it.
     */
    val NavBarHeight = 64.dp
}
