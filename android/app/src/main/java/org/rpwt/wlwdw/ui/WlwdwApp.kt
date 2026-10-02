package org.rpwt.wlwdw.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import org.rpwt.wlwdw.ui.nav.WlwdwDest
import org.rpwt.wlwdw.ui.screen.MapScreen
import org.rpwt.wlwdw.ui.screen.MessageScreen
import org.rpwt.wlwdw.ui.screen.SettingsScreen
import org.rpwt.wlwdw.ui.screen.StatusScreen

/**
 * The bottom bar's tabs.
 *
 * Three of them, and settings is not one: the Material 3 navigation bar
 * guidance is 3-5 peer destinations and rules out single tasks, so settings
 * lives in the top app bar instead.
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
 * The whole UI: three tabs at the bottom, one [NavHost] above them.
 *
 * The bar is the short (64dp) Material 3 navigation bar -- the baseline 80dp
 * one is no longer recommended as of M3 Expressive -- and is hidden on
 * secondary screens, which are reached from the top app bar and back out.
 */
@Composable
fun WlwdwApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = WlwdwTabs.firstOrNull { destination.isAt(it.dest) }

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
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(tab.dest) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null,
                                )
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
                StatusScreen(onOpenSettings = { navController.navigate(WlwdwDest.Settings) })
            }
            composable<WlwdwDest.Map> {
                MapScreen()
            }
            composable<WlwdwDest.Messages> {
                MessageScreen(onOpenSettings = { navController.navigate(WlwdwDest.Settings) })
            }
            composable<WlwdwDest.Settings> {
                SettingsScreen(onBack = { navController.popBackStack() })
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
