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
 * Tab 3: received messages.
 *
 * Carries the settings action too, because the map tab (which sits between)
 * has no app bar and would otherwise be a dead end for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.wlwdw_tab_messages),
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
