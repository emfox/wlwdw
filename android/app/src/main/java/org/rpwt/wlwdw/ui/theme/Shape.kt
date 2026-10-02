package org.rpwt.wlwdw.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Corner radii from the design. Concrete uses:
 *   r-m (14) -- cards and inputs
 *   r-l (20) -- the map's bottom sheet
 *   r-xl (28) -- large containers
 *
 * The pill used by status chips is not a Material slot; ask for
 * RoundedCornerShape(percent = 50) where a chip is drawn.
 */
val WlwdwShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
