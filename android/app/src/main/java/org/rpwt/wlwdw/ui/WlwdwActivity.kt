package org.rpwt.wlwdw.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.rpwt.wlwdw.ui.theme.WlwdwTheme

/**
 * The single Activity of the rewritten UI; everything above it is Compose.
 *
 * It is the launcher, but the pre-rewrite Activities are still registered and
 * still work -- moving the LAUNCHER intent-filter in AndroidManifest.xml back
 * to MainActivity is all it takes to go back to the old entry point.
 */
class WlwdwActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The bottom bar draws to the bottom edge and owns the navigation-bar
        // inset itself, so the window has to be edge-to-edge for that inset to
        // be reported at all.
        enableEdgeToEdge()
        setContent {
            WlwdwTheme {
                WlwdwApp()
            }
        }
    }
}
