package org.rpwt.wlwdw.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.rpwt.wlwdw.ui.theme.WlwdwTheme

/**
 * The single Activity of the rewritten UI; everything above it is Compose.
 *
 * It is the launcher and the only Activity: the pre-rewrite ones went out with
 * the Baidu stack, so there is no second entry point to fall back to.
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
