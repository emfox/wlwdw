package org.rpwt.wlwdw.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.rpwt.wlwdw.R

/**
 * Tab 1: is the device reporting, and what does the server know about it.
 *
 * Settings is opened from here rather than living in the bottom bar -- see
 * [org.rpwt.wlwdw.ui.nav.WlwdwDest.Settings] for why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.wlwdw_tab_status),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.wlwdw_action_settings),
                    )
                }
            },
        )
        ScreenPlaceholder(modifier = Modifier.weight(1f))
    }
}
