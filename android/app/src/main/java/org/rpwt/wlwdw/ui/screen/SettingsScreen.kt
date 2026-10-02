package org.rpwt.wlwdw.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rpwt.wlwdw.BuildConfig
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs
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
 * Every value on this screen is read from [prefs] and every change goes back
 * through a callback, so nothing here holds a copy that could disagree with
 * what is on disk. The custom-host switch still drives the enabled state of the
 * row under it -- it used to be an `android:dependency` attribute, and in
 * Compose that linkage is explicit instead of implied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: WlwdwPrefs,
    onBack: () -> Unit,
    onUseCustomHostChange: (Boolean) -> Unit,
    onCustomHostChange: (String) -> Unit,
    onReportIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // The two dialogs this screen can open. Kept as plain flags rather than a
    // sealed "which dialog" state: there are two, and only one can be open.
    var editingHost by remember { mutableStateOf(false) }
    var editingInterval by remember { mutableStateOf(false) }
    // The host is edited on a draft, so a half-typed address never reaches
    // storage and a cancel really does nothing.
    var hostDraft by remember { mutableStateOf("") }

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
                value = prefs.deviceId.ifEmpty {
                    stringResource(R.string.wlwdw_settings_device_id_none)
                },
                monospaceValue = true,
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_copy_id),
                showDivider = false,
                onClick = { copyDeviceId(context, prefs.deviceId) },
            )

            GroupLabel(stringResource(R.string.wlwdw_settings_server_group))
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_custom_host),
                trailing = {
                    Switch(
                        checked = prefs.useCustomHost,
                        onCheckedChange = onUseCustomHostChange,
                    )
                },
            )
            // Shows the host that is actually in use, not just the configured
            // one: with the switch off the row is the only place the built-in
            // server is named.
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_host),
                value = prefs.serverHost,
                enabled = prefs.useCustomHost,
                onClick = {
                    hostDraft = prefs.customHost
                    editingHost = true
                },
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_interval),
                value = stringResource(
                    R.string.wlwdw_interval_every,
                    prefs.reportIntervalMinutes,
                ),
                showDivider = false,
                onClick = { editingInterval = true },
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
                // Read from the build rather than written down again, so it
                // cannot drift from what was actually installed.
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            SettingRow(
                title = stringResource(R.string.wlwdw_settings_privacy),
                showDivider = false,
                onClick = {},
            )

            Spacer(Modifier.height(20.dp))
        }
    }

    if (editingHost) {
        HostDialog(
            draft = hostDraft,
            onDraftChange = { hostDraft = it },
            onConfirm = {
                onCustomHostChange(hostDraft)
                editingHost = false
            },
            onDismiss = { editingHost = false },
        )
    }

    if (editingInterval) {
        IntervalDialog(
            current = prefs.reportIntervalMinutes,
            onPick = {
                onReportIntervalChange(it)
                editingInterval = false
            },
            onDismiss = { editingInterval = false },
        )
    }
}

@Composable
private fun HostDialog(
    draft: String,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wlwdw_settings_host)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                label = { Text(stringResource(R.string.wlwdw_settings_host_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.wlwdw_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wlwdw_action_cancel))
            }
        },
    )
}

@Composable
private fun IntervalDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wlwdw_settings_interval)) },
        text = {
            Column {
                // The same six choices the old ListPreference offered. The whole
                // row is tappable, not just the radio button.
                WlwdwPrefs.INTERVAL_CHOICES_MINUTES.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(minutes) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = minutes == current,
                            onClick = { onPick(minutes) },
                        )
                        Text(
                            text = stringResource(R.string.wlwdw_interval_every, minutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wlwdw_action_cancel))
            }
        },
    )
}

/**
 * Copy the device id, which is what the web admin needs in order to point a
 * `Category` at this install.
 *
 * Android 13 and later show their own copy confirmation, so the toast is only
 * for the versions that do not.
 */
private fun copyDeviceId(context: android.content.Context, deviceId: String) {
    if (deviceId.isEmpty()) return
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("wlwdw device id", deviceId))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.wlwdw_settings_id_copied, Toast.LENGTH_SHORT).show()
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
