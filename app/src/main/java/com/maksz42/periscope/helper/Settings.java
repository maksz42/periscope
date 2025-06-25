package com.maksz42.periscope.helper;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import com.maksz42.periscope.R;
import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.ui.CameraView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Settings {
  private static Settings Instance;

  public final String PREF_FILE_NAME = "settings";
  private final SharedPreferences prefs;
  private final SharedPreferences.Editor editor;
  public final String IgnoreAspectRatioKey;
  public final String HostKey;
  public final String PortKey;
  public final String IntervalKey;
  public final String ProtocolKey;
  public final String TimeoutKey;
  public final String ImageFormatKey;
  public final String DisplayImplementationKey;
  public final String UserKey;
  public final String PasswordKey;
  public final String SelectedCamerasKey;
  public final String AutoCheckForUpdatesKey;
  public final String DisableCertVerificationKey;
  public final String NextUpdateCheckTimeKey;
  public final String AutostartKey;
  public final String HideStatusBarKey;
  public final String HideNavBarKey;

  /**
   * not thread-safe, expected to be called on the UI thread
   */
  public static Settings getInstance(Context context) {
    if (Instance == null) {
      Instance = new Settings(context.getApplicationContext());
    }
    return Instance;
  }

  private Settings(Context context) {
    PreferenceManager.setDefaultValues(
        context,
        PREF_FILE_NAME,
        MODE_PRIVATE,
        R.xml.settings_preferences,
        true
    );
    prefs = context.getSharedPreferences(PREF_FILE_NAME, MODE_PRIVATE);
    editor = prefs.edit();
    Resources res = context.getResources();
    IgnoreAspectRatioKey = res.getString(R.string.ignore_aspect_ratio_key);
    HostKey = res.getString(R.string.host_key);
    PortKey = res.getString(R.string.port_key);
    IntervalKey = res.getString(R.string.interval_key);
    ProtocolKey = res.getString(R.string.protocol_key);
    TimeoutKey = res.getString(R.string.timeout_key);
    ImageFormatKey = res.getString(R.string.image_format_key);
    DisplayImplementationKey = res.getString(R.string.display_implementation_key);
    UserKey = res.getString(R.string.user_key);
    PasswordKey = res.getString(R.string.password_key);
    SelectedCamerasKey = res.getString(R.string.selected_cameras_key);
    AutoCheckForUpdatesKey = res.getString(R.string.auto_check_for_updates_key);
    DisableCertVerificationKey = res.getString(R.string.disable_cert_verification_key);
    NextUpdateCheckTimeKey = res.getString(R.string.next_update_check_time_key);
    AutostartKey = res.getString(R.string.autostart_key);
    HideStatusBarKey = res.getString(R.string.hide_status_bar_key);
    HideNavBarKey = res.getString(R.string.hide_nav_bar_key);
  }

  private boolean getBoolean(String key) {
    return prefs.getBoolean(key, false);
  }

  private void setBoolean(String key, boolean value) {
    editor.putBoolean(key, value);
  }

  private long getLong(String key) {
    return prefs.getLong(key, -1);
  }

  private void setLong(String key, long value) {
    editor.putLong(key, value);
  }

  private String getString(String key) {
    return prefs.getString(key, null);
  }

  private void setString(String key, String value) {
    editor.putString(key, value);
  }

  public void apply() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
      editor.apply();
    } else {
      editor.commit();
    }
  }

  public boolean getIgnoreAspectRatio() {
    return getBoolean(IgnoreAspectRatioKey);
  }

  public Settings setIgnoreAspectRatio(boolean enable) {
    setBoolean(IgnoreAspectRatioKey, enable);
    return this;
  }

  public String getHost() {
    return getString(HostKey);
  }

  public Settings setHost(String value) {
    setString(HostKey, value);
    return this;
  }

  public int getPort() {
    return Integer.parseInt(getString(PortKey));
  }

  public Settings setPort(int value) {
    setString(PortKey, String.valueOf(value));
    return this;
  }

  public int getInterval() {
    return Integer.parseInt(getString(IntervalKey));
  }

  public Settings setInterval(int value) {
    setString(IntervalKey, String.valueOf(value));
    return this;
  }

  public Client.Protocol getProtocol() {
    return Client.Protocol.valueOf(getString(ProtocolKey));
  }

  public Settings setProtocol(Client.Protocol value) {
    setString(ProtocolKey, String.valueOf(value));
    return this;
  }

  public short getTimeout() {
    return Short.parseShort(getString(TimeoutKey));
  }

  public Settings setTimeout(short value) {
    setString(HostKey, String.valueOf(value));
    return this;
  }

  public Media.ImageFormat getImageFormat() {
    return Media.ImageFormat.valueOf(getString(ImageFormatKey));
  }

  public Settings setImageFormat(Media.ImageFormat value) {
    setString(ImageFormatKey, String.valueOf(value));
    return this;
  }

  public CameraView.DisplayImplementation getDisplayImplementation() {
    return CameraView.DisplayImplementation.valueOf(getString(DisplayImplementationKey));
  }

  public Settings setDisplayImplementation(CameraView.DisplayImplementation value) {
    setString(DisplayImplementationKey, String.valueOf(value));
    return this;
  }

  public String getUser() {
    return getString(UserKey);
  }

  public Settings setUser(String value) {
    setString(UserKey, value);
    return this;
  }

  public String getPassword() {
    return getString(PasswordKey);
  }

  public Settings setPassword(String value) {
    setString(PasswordKey, value);
    return this;
  }

  public boolean getAutoCheckForUpdates() {
    return getBoolean(AutoCheckForUpdatesKey);
  }

  public Settings setAutoCheckForUpdates(boolean enable) {
    setBoolean(AutoCheckForUpdatesKey, enable);
    return this;
  }

  public boolean getDisableCertVerification() {
    return getBoolean(DisableCertVerificationKey);
  }

  public Settings setDisableCertVerification(boolean value) {
    setBoolean(DisableCertVerificationKey, value);
    return this;
  }

  public Settings setSelectedCameras(List<String> cameraNames) {
    setString(SelectedCamerasKey, String.join(",", cameraNames));
    return this;
  }

  public List<String> getSelectedCameras() {
    String selectedCameras = getString(SelectedCamerasKey);
    if (selectedCameras == null) {
      return null;
    } else if (selectedCameras.length() == 0) { // isEmpty()
      return Collections.emptyList();
    }
    return Arrays.asList(selectedCameras.split(","));
  }

  public long getNextUpdateCheckTime() {
    return getLong(NextUpdateCheckTimeKey);
  }

  public Settings setNextUpdateCheckTime(long value) {
    setLong(NextUpdateCheckTimeKey, value);
    return this;
  }

  public boolean getAutostart() {
    return getBoolean(AutostartKey);
  }

  public Settings setAutostart(boolean value) {
    setBoolean(AutostartKey, value);
    return this;
  }

  public boolean getHideStatusBar() {
    return getBoolean(HideStatusBarKey);
  }

  public Settings setHideStatusBar(boolean value) {
    setBoolean(HideStatusBarKey, value);
    return this;
  }

  public boolean getHideNavBar() {
    return getBoolean(HideNavBarKey);
  }

  public Settings setHideNavBar(boolean value) {
    setBoolean(HideNavBarKey, value);
    return this;
  }
}
