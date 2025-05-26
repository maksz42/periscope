package com.maksz42.periscope;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.utils.Misc;
import com.maksz42.periscope.utils.Net;

import java.net.MalformedURLException;

// TODO make non-preference-based settings activity
// this is awful
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
  private final Settings settings = Settings.getInstance(this);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    super.onCreate(savedInstanceState);
    getPreferenceManager().setSharedPreferencesName(settings.PREF_FILE_NAME);
    getPreferenceManager().setSharedPreferencesMode(MODE_PRIVATE);
    addPreferencesFromResource(R.xml.settings_preferences);

    Preference noCertPreference = findPreference(settings.DisableCertVerificationKey);
    noCertPreference.setEnabled(settings.getProtocol() == Client.Protocol.HTTPS);
    Preference protocolPreference = findPreference(settings.ProtocolKey);
    protocolPreference.setOnPreferenceChangeListener((preference, newValue) -> {
      noCertPreference.setEnabled(Client.Protocol.valueOf((String) newValue) == Client.Protocol.HTTPS);
      return true;
    });

    final int minInterval = 200;
    EditTextPreference intervalPreference = (EditTextPreference) findPreference(settings.IntervalKey);
    intervalPreference.setDialogMessage(getString(R.string.interval_preference_dialog_msg, minInterval));
    intervalPreference.setOnPreferenceChangeListener(
        (preference, newValue) -> Integer.parseInt((String) newValue) >= minInterval
    );

    final int maxTimeout = 5000;
    EditTextPreference timeoutPreference = (EditTextPreference) findPreference(settings.TimeoutKey);
    timeoutPreference.setDialogMessage(getString(R.string.timeout_preference_dialog_msg, maxTimeout));
    timeoutPreference.setOnPreferenceChangeListener(
        (preference, newValue) -> Misc.inRange((String) newValue, 0, maxTimeout)
    );

    if (!FrameBuffer.SUPPORTS_WEBP) {
      findPreference(settings.ImageFormatKey).setEnabled(false);
    }

    Preference autostart = findPreference(settings.AutostartKey);
    autostart.setOnPreferenceChangeListener((preference, newValue) -> {
      BootReceiver.setEnabled(this, (Boolean) newValue);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return requestSystemAlertWindowPermission();
      }
      return true;
    });

    getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_settings_title_bar);
    TextView title = findViewById(R.id.title);
    title.setText(R.string.settings_activity_label);
    findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private boolean requestSystemAlertWindowPermission() {
    if(!android.provider.Settings.canDrawOverlays(this)) {
      Intent intent = new Intent(
          android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:" + this.getPackageName())
      );
      startActivity(intent);
      return false;
    }
    return true;
  }

  @Override
  public void onResume() {
    super.onResume();
    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    PreferenceScreen preferenceScreen = getPreferenceScreen();
    int count = preferenceScreen.getPreferenceCount();
    for (int i = 0; i < count; i++) {
      Preference preference = preferenceScreen.getPreference(i);
      updatePreferenceSummary(preference);
    }
  }

  @Override
  public void onPause() {
    getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Preference preference = findPreference(key);
    updatePreferenceSummary(preference);
  }

  private void updatePreferenceSummary(Preference preference) {
    if (preference instanceof EditTextPreference editTextPreference) {
      String text = editTextPreference.getText();
      if (preference.getKey().equals(settings.PasswordKey) && text != null) {
        preference.setSummary("*".repeat(text.length()));
      } else {
        preference.setSummary(text);
      }
    } else if (preference instanceof ListPreference listPreference) {
      preference.setSummary(listPreference.getEntry());
    }
  }

  @Override
  public void onBackPressed() {
    EditTextPreference hostPreference = (EditTextPreference) findPreference(settings.HostKey);
    String host = hostPreference.getText();
    if (host == null || host.isBlank()) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.invalid_host)
          .setPositiveButton(R.string.change_host, null)
          .show();
      return;
    }
    EditTextPreference portPreference = (EditTextPreference) findPreference(settings.PortKey);
    int port;
    try {
      port = Integer.parseInt(portPreference.getText());
    } catch (NumberFormatException e) {
      port = -1;
    }
    if (port < 0) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.invalid_port)
          .setPositiveButton(R.string.change_port, null)
          .show();
      return;
    }
    ListPreference protocolPreference = (ListPreference) findPreference(settings.ProtocolKey);
    Client.Protocol protocol = Client.Protocol.valueOf(protocolPreference.getValue());
    try {
      Client.setup(protocol, host, port);
    } catch (MalformedURLException e) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.invalid_url)
          .setPositiveButton(R.string.change_url, null)
          .show();
      return;
    }
    EditTextPreference user = (EditTextPreference) findPreference("user");
    EditTextPreference password = (EditTextPreference) findPreference("password");
    Client.setCredentials(user.getText(), password.getText());

    Net.configureSSLSocketFactory(this, settings.getDisableCertVerification());
    super.onBackPressed();
  }
}
