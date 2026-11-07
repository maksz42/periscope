package com.maksz42.periscope;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.frigate.Config;
import com.maksz42.periscope.frigate.InvalidResponseException;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.overlay.KeyGate;
import com.maksz42.periscope.overlay.OverlayKeyService;
import com.maksz42.periscope.utils.DialogUtils;
import com.maksz42.periscope.utils.Misc;
import com.maksz42.periscope.utils.Net;

import java.io.IOException;
import java.net.MalformedURLException;

// TODO make non-preference-based settings activity
// this is awful
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
  private static final String TAG = "SettingsActivity";

  private final Settings settings = Settings.getInstance();
  private boolean initialHttpsState;
  private boolean initialNoCertState;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    super.onCreate(savedInstanceState);
    getPreferenceManager().setSharedPreferencesName(settings.PREF_FILE_NAME);
    getPreferenceManager().setSharedPreferencesMode(MODE_PRIVATE);
    addPreferencesFromResource(R.xml.settings_preferences);

    initialHttpsState = (settings.getProtocol() == Client.Protocol.HTTPS);
    initialNoCertState = settings.getDisableCertVerification();
    Preference noCertPreference = findPreference(settings.DisableCertVerificationKey);
    noCertPreference.setEnabled(initialHttpsState);
    CheckBoxPreference rtspTls = (CheckBoxPreference) findPreference(getString(R.string.rtsp_tls_indicator_key));
    rtspTls.setChecked(initialHttpsState);
    Preference protocolPreference = findPreference(settings.ProtocolKey);
    protocolPreference.setOnPreferenceChangeListener((preference, newValue) -> {
      boolean en = Client.Protocol.valueOf((String) newValue) == Client.Protocol.HTTPS;
      noCertPreference.setEnabled(en);
      rtspTls.setChecked(en);
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

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      getPreferenceScreen().removePreference(findPreference(settings.HideNavBarKey));
    }

    setUpOverlayPreferences();

    Preference.OnPreferenceChangeListener portListener =
        (preference, newValue) -> validatePort((String) newValue) > 0;
    findPreference(settings.PortKey).setOnPreferenceChangeListener(portListener);
    findPreference(settings.RtspPortKey).setOnPreferenceChangeListener(portListener);

    findPreference(settings.HostKey).setOnPreferenceChangeListener(
        (preference, newValue) -> validateHost((String) newValue)
    );

    findPreference(getString(R.string.rtsp_fetch_credentials_key)).setOnPreferenceClickListener(p -> {
      if (!commitConfig()) return true;
      new Thread(() -> {
        try {
          Pair<String, String> creds = new Config().getRtspCredentials();
          if (creds.first.equalsIgnoreCase("{FRIGATE_GO2RTC_RTSP_USERNAME}")
              || creds.second.equalsIgnoreCase("{FRIGATE_GO2RTC_RTSP_PASSWORD}")
          ) {
            runOnUiThread(() ->
                DialogUtils.civilShow(
                    new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(R.string.rtsp_fetch_credentials_error_env)
                        .setPositiveButton(android.R.string.ok, null),
                    SettingsActivity.this
                )
            );
          } else {
            runOnUiThread(() -> {
              ((EditTextPreference) findPreference(settings.RtspUserKey)).setText(creds.first);
              ((EditTextPreference) findPreference(settings.RtspPasswordKey)).setText(creds.second);
              DialogUtils.civilShow(
                  new AlertDialog.Builder(SettingsActivity.this)
                      .setTitle(R.string.rtsp_fetch_credentials_success)
                      .setPositiveButton(android.R.string.ok, null),
                  SettingsActivity.this
              );
            });
          }
        } catch (IOException | InterruptedException | InvalidResponseException e) {
          runOnUiThread(() ->
              DialogUtils.civilShow(
                  new AlertDialog.Builder(SettingsActivity.this)
                      .setTitle(R.string.rtsp_fetch_credentials_error_generic)
                      .setPositiveButton(android.R.string.ok, null),
                  SettingsActivity.this
              )
          );
          Log.e(TAG, "Error while fetching RTSP credentials", e);
        }
      }).start();
      return true;
    });

    getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_settings_title_bar);
    TextView title = findViewById(R.id.title);
    title.setText(getTitle());
    findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
  }

  /** The "Watch over TV" screen: needs accessibility overlays (Android 5.1). */
  private void setUpOverlayPreferences() {
    Preference screen = findPreference(getString(R.string.overlay_screen_key));
    if (Build.VERSION.SDK_INT < 22) {
      getPreferenceScreen().removePreference(screen);
      return;
    }
    findPreference(getString(R.string.overlay_status_key)).setOnPreferenceClickListener(p -> {
      try {
        startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
      } catch (android.content.ActivityNotFoundException e) {
        DialogUtils.civilShow(
            new AlertDialog.Builder(this)
                .setMessage(R.string.overlay_no_accessibility_settings)
                .setPositiveButton(android.R.string.ok, null),
            this
        );
      }
      return true;
    });
    findPreference(getString(R.string.overlay_trigger_key_key)).setOnPreferenceClickListener(p -> {
      chooseTriggerKey();
      return true;
    });
    findPreference(getString(R.string.overlay_cameras_key)).setOnPreferenceClickListener(p -> {
      startActivity(new Intent(this, CamerasOrderActivity.class)
          .putExtra(CamerasOrderActivity.EXTRA_OVERLAY_LIST, true));
      return true;
    });
    updateOverlaySummaries();
  }

  private void updateOverlaySummaries() {
    if (Build.VERSION.SDK_INT < 22) return;
    Preference status = findPreference(getString(R.string.overlay_status_key));
    if (status == null) return;
    status.setSummary(OverlayKeyService.isEnabled(this)
        ? R.string.overlay_status_on
        : R.string.overlay_status_off);
    findPreference(getString(R.string.overlay_trigger_key_key)).setSummary(
        getString(R.string.overlay_trigger_summary, triggerName(settings.getOverlayTriggerKey())));
    java.util.List<String> listCameras = settings.getOverlayCameras();
    findPreference(getString(R.string.overlay_cameras_key)).setSummary(
        listCameras == null ? getString(R.string.overlay_cameras_same_as_grid)
            : listCameras.isEmpty() ? getString(R.string.no_cameras_selected)
            : android.text.TextUtils.join(", ", listCameras));
  }

  @RequiresApi(22)
  private static String triggerName(int keyCode) {
    return KeyGate.label(KeyEvent.keyCodeToString(keyCode));
  }

  /** Asks for a press of the button to use, and keeps it if holding it can't disturb the show. */
  @RequiresApi(22)
  private void chooseTriggerKey() {
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(R.string.overlay_trigger)
        .setMessage(R.string.overlay_trigger_press)
        .setNegativeButton(android.R.string.cancel, null)
        .create();
    dialog.setOnKeyListener((d, keyCode, event) -> {
      if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return false;
      // Keys that move around the dialog and close it keep doing so.
      if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
          || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_UP
          || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
          || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
        return false;
      }
      if (!KeyGate.canTrigger(keyCode)) {
        dialog.setMessage(getString(R.string.overlay_trigger_refused, triggerName(keyCode)));
        return true;
      }
      settings.setOverlayTriggerKey(keyCode).apply();
      updateOverlaySummaries();
      dialog.dismiss();
      return true;
    });
    DialogUtils.civilShow(dialog, this);
  }

  private int parsePort(String value) {
    try {
      int port = Integer.parseInt(value);
      if (Misc.inRange(port, 1, 65535)) {
        return port;
      }
    } catch (NumberFormatException ignored) { }
    return -1;
  }

  private int validatePort(String value) {
    int port = parsePort(value);
    if (port < 0) {
      new AlertDialog.Builder(SettingsActivity.this)
          .setTitle(R.string.invalid_port)
          .setPositiveButton(R.string.change_port, null)
          .show();
    }
    return port;
  }

  private boolean validateHost(String value) {
    if (value == null || value.isBlank()) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.invalid_host)
          .setPositiveButton(R.string.change_host, null)
          .show();
      return false;
    }
    return true;
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
    postProcessPreferences(preferenceScreen);
    // Back from the system's accessibility settings: show whether the service is on now.
    updateOverlaySummaries();
  }

  private void postProcessPreferences(PreferenceScreen preferenceScreen) {
    int count = preferenceScreen.getPreferenceCount();
    for (int i = 0; i < count; i++) {
      Preference preference = preferenceScreen.getPreference(i);
      updatePreferenceSummary(preference);
      if (preference instanceof PreferenceScreen nestedPreferenceScreen) {
        nestedPreferenceScreen.setOnPreferenceClickListener(p -> {
          patchPreferenceScreenDialog((PreferenceScreen) p);
          return false;
        });
        postProcessPreferences(nestedPreferenceScreen);
      }
    }
  }

  private void patchPreferenceScreenDialog(PreferenceScreen preferenceScreen) {
    Dialog dialog = preferenceScreen.getDialog();
    View titleView = dialog.findViewById(android.R.id.title);
    if (titleView == null) return;
    ViewGroup parent = (ViewGroup) titleView.getParent();
    if (parent instanceof FrameLayout) {
      View activityTitleContainer = (View) findViewById(R.id.title).getParent().getParent();
      parent.getLayoutParams().height = activityTitleContainer.getHeight();
      parent.requestLayout();
    }
    int index = parent.indexOfChild(titleView);
    parent.removeView(titleView);
    View newTitle = getLayoutInflater().inflate(
        R.layout.custom_settings_title_bar,
        parent,
        false
    );
    TextView titleTextView = newTitle.findViewById(R.id.title);
    titleTextView.setId(android.R.id.title);
    titleTextView.setText(preferenceScreen.getTitle());
    newTitle.findViewById(R.id.btn_back).setOnClickListener(v -> dialog.dismiss());
    parent.addView(newTitle, index);
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

  private static boolean isPasswordInputType(int inputType) {
    final int variation =
        inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
    return variation
        == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
//        || variation
//        == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
//        || variation
//        == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD)
        ;
  }

  private void updatePreferenceSummary(Preference preference) {
    if (preference instanceof EditTextPreference editTextPreference) {
      String text = editTextPreference.getText();
      int it = editTextPreference.getEditText().getInputType();
      if (text != null && isPasswordInputType(it)) {
        preference.setSummary("*".repeat(text.length()));
      } else {
        preference.setSummary(text);
      }
    } else if (preference instanceof ListPreference listPreference) {
      preference.setSummary(listPreference.getEntry());
    }
  }

  private boolean commitConfig() {
    EditTextPreference hostPreference = (EditTextPreference) findPreference(settings.HostKey);
    String host = hostPreference.getText();
    if (!validateHost(host)) return false;
    EditTextPreference portPreference = (EditTextPreference) findPreference(settings.PortKey);
    int port = validatePort(portPreference.getText());
    if (port < 0) return false;
    ListPreference protocolPreference = (ListPreference) findPreference(settings.ProtocolKey);
    Client.Protocol protocol = Client.Protocol.valueOf(protocolPreference.getValue());
    try {
      Client.setup(protocol, host, port);
    } catch (MalformedURLException e) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.invalid_url)
          .setPositiveButton(R.string.change_url, null)
          .show();
      return false;
    }
    EditTextPreference user = (EditTextPreference) findPreference("user");
    EditTextPreference password = (EditTextPreference) findPreference("password");
    Client.setCredentials(user.getText(), password.getText());

    if (settings.getProtocol() == Client.Protocol.HTTPS
        && (!initialHttpsState || initialNoCertState != settings.getDisableCertVerification()))
    {
      Net.configureSSLSocketFactory(this, settings.getDisableCertVerification());
    }
    return true;
  }

  @Override
  public void onBackPressed() {
    if (!commitConfig()) return;
    super.onBackPressed();
  }
}
