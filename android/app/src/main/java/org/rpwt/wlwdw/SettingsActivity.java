package org.rpwt.wlwdw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.UUID;

public class SettingsActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_activity);
		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.settings, new SettingsFragment())
					.commit();
		}
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	public static class SettingsFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.pref_general, rootKey);

			// Surface this install's tracking device id. The device must match
			// Category.devid on the server (used to route pushes / accept trail
			// uploads), so tapping the row copies the value to the clipboard for
			// the user to paste into the web admin. When no id exists yet it is
			// generated on the spot (reinstall / fresh start before the tracking
			// screen was ever opened).
			Preference deviceIdPref = findPreference("device_id_display");
			if (deviceIdPref != null) {
				deviceIdPref.setOnPreferenceClickListener(preference -> {
					copyDeviceId();
					return true;
				});
				refreshDeviceIdSummary(deviceIdPref);
			}
		}

		private void refreshDeviceIdSummary(Preference deviceIdPref) {
			Context context = getContext();
			if (context == null) {
				return;
			}
			String uuid = PreferenceManager.getDefaultSharedPreferences(context)
					.getString("app_uuid", "");
			deviceIdPref.setSummary(uuid.isEmpty()
					? "（尚未生成，点击本行自动生成并复制）"
					: uuid + "\n点击复制，用于在 web 后台更新该设备的 devid");
		}

		private void copyDeviceId() {
			Context context = getContext();
			if (context == null) {
				return;
			}
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			String uuid = prefs.getString("app_uuid", "");
			if (uuid.isEmpty()) {
				uuid = UUID.randomUUID().toString();
				prefs.edit().putString("app_uuid", uuid).apply();
			}
			ClipboardManager clipboard = (ClipboardManager) context
					.getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setPrimaryClip(ClipData.newPlainText("wlwdw device id", uuid));
			Toast.makeText(context, "设备 ID 已复制，可粘贴到 web 后台更新对应 Category 的 devid",
					Toast.LENGTH_LONG).show();

			Preference deviceIdPref = findPreference("device_id_display");
			if (deviceIdPref != null) {
				refreshDeviceIdSummary(deviceIdPref);
			}
		}
	}
}