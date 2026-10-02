package org.rpwt.wlwdw.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs
import org.rpwt.wlwdw.ui.nav.WlwdwDest
import org.rpwt.wlwdw.ui.screen.ConsentScreen
import org.rpwt.wlwdw.ui.screen.MapScreen
import org.rpwt.wlwdw.ui.screen.MessageScreen
import org.rpwt.wlwdw.ui.screen.SettingsScreen
import org.rpwt.wlwdw.ui.screen.StatusScreen

/**
 * The bottom bar's tabs.
 *
 * Three of them, and settings is not one: the Material 3 navigation bar
 * guidance is 3-5 peer destinations and rules out single tasks, so settings
 * lives in the status screen's top app bar instead.
 */
private data class WlwdwTab(
    val dest: WlwdwDest,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val WlwdwTabs = listOf(
    WlwdwTab(
        dest = WlwdwDest.Status,
        labelRes = R.string.wlwdw_tab_status,
        selectedIcon = Icons.Filled.LocationOn,
        unselectedIcon = Icons.Outlined.LocationOn,
    ),
    WlwdwTab(
        dest = WlwdwDest.Map,
        labelRes = R.string.wlwdw_tab_map,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
    ),
    WlwdwTab(
        dest = WlwdwDest.Messages,
        labelRes = R.string.wlwdw_tab_messages,
        selectedIcon = Icons.Filled.Email,
        unselectedIcon = Icons.Outlined.Email,
    ),
)

/**
 * The whole UI: the first-run consent page, then three tabs over one [NavHost].
 *
 * The bar is the short (64dp) Material 3 navigation bar -- the baseline 80dp
 * one is no longer recommended as of M3 Expressive -- and is hidden on the
 * screens that are not tabs, which are reached from a top app bar and backed
 * out of.
 *
 * Consent is a gate in front of the tabs rather than a destination in them.
 * That is what makes it appear exactly once per install: the answer is stored,
 * so the next launch starts at the tabs and never builds the page at all. As a
 * destination it needed a `popUpTo` to keep it off the back stack, and the
 * back stack is a fragile place to keep a compliance decision.
 */
@Composable
fun WlwdwApp(
    navController: NavHostController = rememberNavController(),
    // Not named `viewModel`: that would shadow the `viewModel()` factory used as
    // this very default, and a parameter cannot be resolved in its own default.
    appViewModel: AppViewModel = viewModel(),
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loaded = settings

    when {
        // One frame while DataStore is read. Deliberately blank rather than a
        // guess: this is what stops the consent page from flashing for a user
        // who has already answered it.
        loaded == null -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {}

        !loaded.consentAccepted -> ConsentScreen(
            onAgree = appViewModel::acceptConsent,
            // Declining leaves the app. Forcing consent with a non-cancellable
            // dialog is what the design rules out.
            onDecline = { (context as? Activity)?.finish() },
        )

        else -> Tabs(loaded, navController, appViewModel)
    }
}

@Composable
private fun Tabs(
    settings: WlwdwPrefs,
    navController: NavHostController,
    appViewModel: AppViewModel,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = WlwdwTabs.firstOrNull { destination.isAt(it.dest) }

    // Collected here rather than inside the messages tab, because the badge in
    // the bar needs the count from every tab. The count is derived from the
    // list it summarises, so the two cannot disagree.
    val messages by appViewModel.messages.collectAsStateWithLifecycle()
    val unreadCount = messages.count { it.unread }

    val tracking by appViewModel.tracking.collectAsStateWithLifecycle()
    val push by appViewModel.push.collectAsStateWithLifecycle()

    // The permission can change while the app is in the background -- granting
    // "all the time" happens on a Settings page -- so re-read it whenever the
    // app comes back rather than trusting what was true when the loop started.
    LifecycleResumeEffect(Unit) {
        appViewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    // A message the user cannot see is not a message. Android 13 made showing a
    // notification a runtime permission, and a denied one is silent: the push
    // arrives, is stored, and nobody knows. Below 33 the permission does not
    // exist, so asking is both pointless and refused.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Foreground and background are separate requests on purpose. From Android
    // 11 the system refuses to grant "all the time" in the same dialog as the
    // while-in-use grant, and asking for it first gets both refused. So: ask
    // for while-in-use, start tracking, and let the status screen's notice ask
    // for the rest once there is a reason to.
    val foregroundPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        appViewModel.refreshPermissions()
        appViewModel.startTracking()
    }
    val backgroundPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { appViewModel.refreshPermissions() }

    val requestForeground = {
        foregroundPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
    // On Android 11+ this call is what routes the user to the app's location
    // settings page; the dialog alone is not allowed to offer "all the time".
    val requestBackground = {
        backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    Scaffold(
        // The screens own their insets: a screen's top app bar applies the
        // status bar inset itself and the navigation bar applies the navigation
        // bar inset, so Scaffold must not apply them a second time.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selectedTab != null) {
                ShortNavigationBar {
                    WlwdwTabs.forEach { tab ->
                        val selected = tab == selectedTab
                        // Unread mail is the guidance's large badge: a count,
                        // not a hand-drawn dot.
                        val unread = tab.dest == WlwdwDest.Messages && unreadCount > 0
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(tab.dest) },
                            icon = {
                                val image = if (selected) tab.selectedIcon else tab.unselectedIcon
                                if (unread) {
                                    BadgedBox(
                                        badge = { Badge { Text(unreadCount.toString()) } },
                                    ) {
                                        Icon(imageVector = image, contentDescription = null)
                                    }
                                } else {
                                    Icon(imageVector = image, contentDescription = null)
                                }
                            },
                            // Always shown: the guidance requires a visible label
                            // on every destination, never an icon alone.
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WlwdwDest.Status,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable<WlwdwDest.Status> {
                StatusScreen(
                    state = tracking,
                    push = push,
                    deviceId = settings.deviceId,
                    reportIntervalMinutes = settings.reportIntervalMinutes,
                    onOpenSettings = { navController.navigate(WlwdwDest.Settings) },
                    onToggleTracking = {
                        when {
                            tracking.running -> appViewModel.stopTracking()
                            // Never start a tracker without the permission it
                            // needs: the request and the start are one action,
                            // and the callback starts the loop once granted.
                            !tracking.hasForeground -> requestForeground()
                            else -> appViewModel.startTracking()
                        }
                    },
                    onRequestPermission = {
                        if (tracking.hasForeground) requestBackground() else requestForeground()
                    },
                )
            }
            composable<WlwdwDest.Map> {
                MapScreen()
            }
            composable<WlwdwDest.Messages> {
                MessageScreen(
                    messages = messages,
                    onMarkAllRead = appViewModel::markAllRead,
                )
            }
            composable<WlwdwDest.Settings> {
                SettingsScreen(
                    prefs = settings,
                    onBack = { navController.popBackStack() },
                    onUseCustomHostChange = appViewModel::setUseCustomHost,
                    onCustomHostChange = appViewModel::setCustomHost,
                    onReportIntervalChange = appViewModel::setReportIntervalMinutes,
                )
            }
        }
    }
}

/** True when this destination is [dest] or nested under it. */
private fun NavDestination?.isAt(dest: WlwdwDest): Boolean =
    this?.hierarchy?.any { it.hasRoute(dest::class) } == true

/**
 * Switch tabs the way the guidance expects: one entry per tab rather than a
 * growing back stack, with each tab's own state saved and restored.
 */
private fun NavHostController.switchTab(dest: WlwdwDest) {
    navigate(dest) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
