package org.rpwt.wlwdw.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.rpwt.wlwdw.BuildConfig
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.data.model.Report
import org.rpwt.wlwdw.tracking.TrackingState
import org.rpwt.wlwdw.ui.component.StatusPill
import org.rpwt.wlwdw.ui.component.statusTitle
import org.rpwt.wlwdw.ui.component.statusTone

/**
 * Tab 2: the map.
 *
 * The map is neither drawn here nor drawn natively: it is the website's own
 * JavaScript (`public/js/wlwdw-map/`) running in a WebView over the 天地图 JS
 * API. That layer is the part of this project that took the most iteration to
 * get right -- the coordinate strategy, which SDK classes are safe to touch
 * before `components.js` lands, the note layer having to be stacked separately,
 * `drawn[]` + removeOverLay instead of clearOverlays() -- so the app runs that
 * same file rather than a second implementation of it that would rot.
 *
 * Where the app differs from the website is the data. The website fetches
 * /anchor/list, /category/hierarchy and /trail/{catid}, all of which require a
 * logged-in user. A phone in the field has no account and needs none: it draws
 * the positions it took itself, handed to the page as JSON over the bridge
 * below. That is why there is no password anywhere in this app.
 *
 * The shell is `assets/web/index.html`; the JS it loads is synced out of
 * `public/` at build time by the `syncWebAssets` task, so the repository still
 * holds exactly one copy of the map code.
 *
 * Full screen on purpose -- no top app bar. The pre-rewrite map kept an
 * ActionBar above it, which cost 56dp of usable height for nothing; the
 * tracking state that the bar was standing in for is the floating chip here.
 */
@Composable
fun MapScreen(
    reports: List<Report>,
    tracking: TrackingState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The label the map draws on this device's own marker, and what its info
    // window names the unit: the same word the pre-rewrite screen used.
    val title = stringResource(R.string.wlwdw_map_self)

    // The page bridge is a plain remembered object rather than Compose state:
    // AndroidView's factory runs once and needs somewhere to hand the WebView
    // to, and the two fields on it that the UI does read are themselves
    // snapshot state, so the screen still recomposes when they change.
    val bridge = remember { MapPageBridge() }

    // [reports] arrives newest-first (the log is read back in insert order
    // descending), so the payload builder re-reverses it; see mapPayload.
    val payload = remember(reports, title) { mapPayload(reports, title) }
    val hasPoints = remember(reports) {
        reports.any { it.latitude != null && it.longitude != null }
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    LaunchedEffect(payload) { bridge.update(payload) }

    // Silence past this long means the SDK never arrived: no network, or
    // 天地图 refusing the key. Either way the user gets a reason and a retry
    // rather than a green rectangle.
    LaunchedEffect(bridge.attempt) {
        if (bridge.boot == MapBoot.Loading) {
            delay(MapBootTimeoutMillis)
            if (bridge.boot == MapBoot.Loading) bridge.boot = MapBoot.Failed
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> createMapWebView(ctx, assetLoader, bridge) },
        )

        // Top centre: the tracking chip. It has to exist here because splitting
        // the app into tabs means the map can no longer see the status screen.
        StatusPill(
            text = stringResource(statusTitle(tracking.status)),
            tone = statusTone(tracking.status),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        )

        MapFab(
            icon = Icons.Filled.MyLocation,
            contentDescription = stringResource(R.string.wlwdw_map_locate),
            onClick = { bridge.fit() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 14.dp),
        )

        when {
            bridge.boot == MapBoot.Loading -> MapNotice(
                text = stringResource(R.string.wlwdw_map_loading),
                showSpinner = true,
                modifier = Modifier.align(Alignment.Center),
            )

            bridge.boot == MapBoot.Failed -> MapNotice(
                text = stringResource(R.string.wlwdw_map_failed),
                action = stringResource(R.string.wlwdw_map_retry),
                onAction = { bridge.reload() },
                modifier = Modifier.align(Alignment.Center),
            )

            // An empty map is indistinguishable from a broken one, and the
            // difference matters: this device simply has nothing to show yet.
            !hasPoints -> MapNotice(
                text = stringResource(R.string.wlwdw_map_empty),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
            )
        }
    }
}

private const val MapBootTimeoutMillis = 15_000L

/** Where the bundled shell is addressed once the asset loader is in play. */
private const val MapShellUrl = "https://appassets.androidplatform.net/assets/web/index.html"

/** Must match the name the shell calls back on. */
private const val BridgeName = "WlwdwHost"

/** Where the page's console output lands; `adb logcat -s WlwdwMap`. */
private const val MapLogTag = "WlwdwMap"

/** What the page has told us about its own start-up. */
private enum class MapBoot { Loading, Ready, Failed }

/** The payload that means "nothing to draw"; the page's starting value too. */
private const val EmptyPayload = """{"me":null,"trail":[]}"""

/**
 * How many of the newest positions the map is given.
 *
 * The report log holds up to 1440 rows and the map draws a marker plus a text
 * label per point, so handing it all of them would put ~3000 overlays on the
 * SDK at once and make panning unusable. The newest few hundred are what anyone
 * actually reads as "the trail".
 */
private const val MaxTrailPoints = 200

/**
 * The WebView and the shell's callbacks, held together.
 *
 * A plain object rather than composable state: `AndroidView`'s factory runs
 * once, outside recomposition, so the WebView it creates needs a home that
 * outlives that call. [boot] and [attempt] are snapshot state because the
 * screen reads them to decide what to draw over the map.
 */
private class MapPageBridge {

    var boot by mutableStateOf(MapBoot.Loading)
        internal set

    /** Bumped on every reload so the timeout effect starts over. */
    var attempt by mutableStateOf(0)
        internal set

    private var view: WebView? = null
    private var loaded = false
    private var payload = EmptyPayload

    fun attach(webView: WebView) {
        view = webView
        loaded = false
    }

    /** New positions arrived. Held even before the page is up. */
    fun update(json: String) {
        payload = json
        push()
    }

    /** The page finished loading and is ready to be booted. */
    fun pageLoaded() {
        loaded = true
        push()
    }

    /** Called from the JS thread; already posted back to the main one. */
    fun onState(state: String) {
        boot = when {
            state == "ready" || state.startsWith("applied") -> MapBoot.Ready
            else -> MapBoot.Failed
        }
    }

    fun reload() {
        boot = MapBoot.Loading
        loaded = false
        attempt++
        view?.reload()
    }

    /** Re-frame the map on everything it is showing. */
    fun fit() {
        view?.evaluateJavascript("window.wlwdwFit()", null)
    }

    /**
     * Hand the page its key and its data.
     *
     * `wlwdwBoot` is idempotent on the page, so sending both every time costs
     * nothing and removes the need to know which of "the page is ready" and
     * "the data changed" happened first.
     */
    private fun push() {
        val target = view ?: return
        if (!loaded) return
        target.evaluateJavascript(
            "window.wlwdwBoot('${BuildConfig.TIANDITU_MAP_KEY}');" +
                "window.wlwdwApply($payload);",
            null,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createMapWebView(
    context: Context,
    assetLoader: WebViewAssetLoader,
    bridge: MapPageBridge,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    setBackgroundColor(android.graphics.Color.TRANSPARENT)

    // The page is a bundled file that runs this project's own map JS. There is
    // no remote or third-party document in it, so script and the JS bridge under
    // it do not widen the attack surface the way they would for a page loaded
    // from the network.
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    // The map SDK handles pinch-zoom; letting the page zoom as well would fight
    // it.
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false

    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

    addJavascriptInterface(
        object {
            @JavascriptInterface
            fun onMapState(state: String) {
                // Invoked on a WebView-owned thread, not the main one. Posting
                // through the view is what makes it safe to touch snapshot
                // state from here.
                post { bridge.onState(state) }
            }
        },
        BridgeName,
    )

    webViewClient = object : WebViewClientCompat() {
        // Everything under /assets/ is served by the loader, which is what gives
        // the page an https origin instead of the "null" one file:// produces.
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

        override fun onPageFinished(view: WebView, url: String) {
            bridge.pageLoaded()
        }
    }

    // The page is the one part of this app that has no test and no stack trace
    // of its own, so its console goes to logcat under a tag of its own. The map
    // JS and the 天地图 SDK both report failures there and nowhere else.
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            Log.d(MapLogTag, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
            return true
        }
    }

    bridge.attach(this)
    loadUrl(MapShellUrl)
}

/**
 * The map's data, in the shape `core.js` already understands.
 *
 * The field names are the website's on purpose: `pointInfo()` reads `title` and
 * `updatetime.date`, and `timeLabel()` reads `time` as epoch milliseconds.
 * Matching them here is what keeps the page free of a translation layer.
 */
@Serializable
private data class MapPayload(
    val me: MapPin? = null,
    val trail: List<MapPoint> = emptyList(),
)

@Serializable
private data class MapPin(
    val title: String,
    val lat: Double,
    val lng: Double,
    val updatetime: MapTime,
)

@Serializable
private data class MapTime(val date: String)

@Serializable
private data class MapPoint(val lat: Double, val lng: Double, val time: Long)

private val mapJson = Json { encodeDefaults = true }

private val mapTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Turn the report log into the page's payload.
 *
 * [reports] is newest-first, so the newest positioned row is the current
 * position and everything after it is history. The two are kept apart on
 * purpose: the website draws a pin for where a unit is and separate markers for
 * where it has been (`/trail/{catid}` holds the history), and sending the same
 * point as both would stack two icons and two labels on one spot.
 *
 * The history is re-reversed into chronological order, which is the order the
 * markers read in.
 *
 * Rows without coordinates are skipped rather than sent as zeros: an attempt
 * that failed before there was a fix has no position, and `0,0` is in the Gulf
 * of Guinea. This is also the rule the website's `placed()` applies.
 */
private fun mapPayload(reports: List<Report>, title: String): String {
    val placed = reports.filter { it.latitude != null && it.longitude != null }
    if (placed.isEmpty()) return EmptyPayload

    val newest = placed.first()
    val trail = placed.drop(1).take(MaxTrailPoints).asReversed().map { report ->
        MapPoint(
            lat = report.latitude!!,
            lng = report.longitude!!,
            time = report.at,
        )
    }

    return mapJson.encodeToString(
        MapPayload(
            me = MapPin(
                title = title,
                lat = newest.latitude!!,
                lng = newest.longitude!!,
                updatetime = MapTime(formatMapTime(newest.at)),
            ),
            trail = trail,
        ),
    )
}

private fun formatMapTime(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(mapTimeFormat)

/** A message over the map: why it is not there, or that it is empty. */
@Composable
private fun MapNotice(
    text: String,
    modifier: Modifier = Modifier,
    showSpinner: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** A floating control over the map: round, on the surface colour, lifted. */
@Composable
private fun MapFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(40.dp),
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
