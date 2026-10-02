package org.rpwt.wlwdw.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.ui.component.SettingRow
import org.rpwt.wlwdw.ui.demo.DemoData
import org.rpwt.wlwdw.ui.theme.WlwdwDimens

/**
 * Settings: device id, server, reporting interval, diagnostics, about.
 *
 * A secondary screen, so it gets a back arrow and no place in the bottom bar.
 * The same four settings the old `PreferenceScreen` carried are all here; the
 * diagnostics group is new, and is where the debug readouts that used to sit on
 * the main location screen now live -- useful to whoever maintains this, noise
 * to everyone else.
 *
 * The custom-host switch drives the enabled state of the row under it. It used
 * to be an `android:dependency` attribute; in Compose that linkage is explicit,
 * which is why it is a `remember`ed state here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var useCustomHost by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.wlwdw_title_settings),
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WlwdwDimens.PagePadding),
        ) {
            GroupLabel(stringResource(R.string.wlwdw_settings_device_group))
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_device_id),
                subtitle = stringResource(R.string.wlwdw_settings_device_id_sub),
                value = DemoData.deviceId,
                monospaceValue = true,
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_copy_id),
                showDivider = false,
                onClick = {},
            )

            GroupLabel(stringResource(R.string.wlwdw_settings_server_group))
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_custom_host),
                trailing = {
                    Switch(checked = useCustomHost, onCheckedChange = { useCustomHost = it })
                },
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_host),
                value = DemoData.host,
                enabled = useCustomHost,
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_interval),
                value = DemoData.reportInterval,
                showDivider = false,
                onClick = {},
            )

            GroupLabel(stringResource(R.string.wlwdw_settings_diagnostics_group))
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_recent_reports),
                subtitle = stringResource(R.string.wlwdw_settings_recent_reports_sub),
                onClick = {},
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_logs),
                value = DemoData.logCount,
                showDivider = false,
                onClick = {},
            )

            GroupLabel(stringResource(R.string.wlwdw_settings_about_group))
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_version),
                value = DemoData.version,
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_privacy),
                showDivider = false,
                onClick = {},
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight(620),
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 2.dp),
    )
}
