package org.rpwt.wlwdw.ui.screen

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.rpwt.wlwdw.R

/**
 * The privacy policy, shown as the website draws it.
 *
 * The text is not a string resource and not a Kotlin file: it is
 * `public/privacy.html`, the same file the website serves at `/privacy.html`,
 * copied into the APK's assets by the `syncWebAssets` build task. A legal text
 * is amended, and the failure mode of two copies is worse than for code -- the
 * app would keep showing wording that no longer matches what it does, which is
 * the one thing a privacy policy exists not to do. So: one file, rendered by a
 * WebView here and by the web server there.
 *
 * That is why this is a WebView and not a Compose screen of `Text()`s. The
 * page carries its own typography and its own light/dark handling
 * (`prefers-color-scheme`), and it can hold a link the OS knows how to open.
 *
 * No JavaScript: the document does not need any, and `javaScriptEnabled` is
 * left at its default of off. The neighbouring map screen turns it on because
 * the map is a program; this is a page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Under the same https origin the map shell uses, so the page is a normal
    // same-origin document rather than a file:// one with a null origin.
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val background = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.wlwdw_settings_privacy),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.wlwdw_action_back),
                    )
                }
            },
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> createPrivacyWebView(ctx, assetLoader, background.toArgb()) },
            // The page paints its own background, but repainting the view keeps
            // a light rectangle from showing through if the theme flips while
            // the page is still loading.
            update = { it.setBackgroundColor(background.toArgb()) },
        )
    }
}

/** Where the bundled policy is addressed once the asset loader is in play. */
private const val PrivacyPageUrl = "https://appassets.androidplatform.net/assets/web/privacy.html"

/** The origin the asset loader serves; anything else is somebody else's. */
private const val AssetHost = "appassets.androidplatform.net"

private const val LogTag = "WlwdwPrivacy"

private fun createPrivacyWebView(
    context: Context,
    assetLoader: WebViewAssetLoader,
    background: Int,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    setBackgroundColor(background)

    // A policy is long and people read it at their own size, so pinch-zoom
    // works; the on-screen zoom buttons do not, they only cover the text.
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false

    // WebView ignores the system font size unless it is told, which would make
    // the one page that must be readable the one page that cannot be enlarged.
    settings.textZoom = (resources.configuration.fontScale * 100).toInt()

    webViewClient = object : WebViewClientCompat() {
        // Everything under /assets/ comes from the loader; nothing else in the
        // document is fetched except the fonts the OS already has.
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url
            if (url.host == AssetHost) return false
            // mailto: cannot be handled here at all, and there is no reason to
            // browse inside a legal document. Hand it to whatever the user has.
            // Consumed either way: letting the WebView try would replace the
            // policy with an error page, which is worse than nothing happening.
            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                .onFailure { Log.w(LogTag, "no activity for $url", it) }
            return true
        }
    }

    loadUrl(PrivacyPageUrl)
}
