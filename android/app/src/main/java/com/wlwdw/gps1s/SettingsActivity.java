package com.wlwdw.gps1s;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

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

			// Surface this install's tracking device id so the user can copy it
			// into the matching Category.devid on the web (server must use the
			// same value to route pushes / accept trail uploads).
			Preference deviceIdPref = findPreference("device_id_display");
			if (deviceIdPref != null) {
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
						getContext());
				String uuid = prefs.getString("app_uuid", "");
				deviceIdPref.setSummary(uuid.isEmpty()
						? "（尚未生成，启动 App 后自动分配）"
						: uuid);
			}
		}
	}
}