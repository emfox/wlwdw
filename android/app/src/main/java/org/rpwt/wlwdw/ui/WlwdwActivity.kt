package org.rpwt.wlwdw.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import org.rpwt.wlwdw.push.WlwdwNotifications
import org.rpwt.wlwdw.ui.theme.WlwdwTheme

/**
 * The single Activity of the rewritten UI; everything above it is Compose.
 *
 * It is the launcher and the only Activity: the pre-rewrite ones went out with
 * the Baidu stack, so there is no second entry point to fall back to.
 *
 * It is also where a notification tap lands, which is why it holds anything at
 * all: the tab a notification asked for has to survive from the Intent into the
 * composition, and the composition is created by `setContent` rather than by
 * the Intent.
 */
class WlwdwActivity : ComponentActivity() {

    /**
     * What a notification asked to open, if it asked for anything.
     *
     * State rather than a one-shot event because there are two ways in and both
     * have to end at the same place: a cold start arrives through [onCreate],
     * and a tap while the app is already open arrives through [onNewIntent].
     * The UI clears it once acted on, so backing out of the inbox stays backed
     * out instead of being dragged forward again on the next recomposition.
     */
    private val requestedOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The bottom bar draws to the bottom edge and owns the navigation-bar
        // inset itself, so the window has to be edge-to-edge for that inset to
        // be reported at all.
        enableEdgeToEdge()
        requestedOpen.value = intent?.getStringExtra(WlwdwNotifications.EXTRA_OPEN)
        setContent {
            WlwdwTheme {
                WlwdwApp(
                    requestedOpen = requestedOpen.value,
                    onOpenHandled = { requestedOpen.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Replace the Activity's Intent as well as reading it: without this
        // `getIntent()` keeps returning the Intent the Activity was created
        // with, and a later configuration change would re-apply the stale one.
        setIntent(intent)
        requestedOpen.value = intent.getStringExtra(WlwdwNotifications.EXTRA_OPEN)
    }
}
