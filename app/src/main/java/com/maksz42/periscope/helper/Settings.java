package com.maksz42.periscope.helper;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
  public final SharedPreferences prefs;
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
  }

  private boolean getBoolean(String key) {
    return prefs.getBoolean(key, false);
  }

  private void setBoolean(String key, boolean value) {
    prefs.edit().putBoolean(key, value).commit();
  }

  private String getString(String key) {
    return prefs.getString(key, null);
  }

  private void setString(String key, String value) {
    prefs.edit().putString(key, value).commit();
  }

  public boolean getIgnoreAspectRatio() {
    return getBoolean(IgnoreAspectRatioKey);
  }

  public void setIgnoreAspectRatio(boolean enable) {
    setBoolean(IgnoreAspectRatioKey, enable);
  }

  public String getHost() {
    return getString(HostKey);
  }

  public void setHost(String value) {
    setString(HostKey, value);
  }

  public int getPort() {
    return Integer.parseInt(getString(PortKey));
  }

  public void setPort(int value) {
    setString(PortKey, String.valueOf(value));
  }

  public int getInterval() {
    return Integer.parseInt(getString(IntervalKey));
  }

  public void setInterval(int value) {
    setString(IntervalKey, String.valueOf(value));
  }

  public Client.Protocol getProtocol() {
    return Client.Protocol.valueOf(getString(ProtocolKey));
  }

  public void setProtocol(Client.Protocol value) {
    setString(ProtocolKey, String.valueOf(value));
  }

  public short getTimeout() {
    return Short.parseShort(getString(TimeoutKey));
  }

  public void setTimeout(short value) {
    setString(HostKey, String.valueOf(value));
  }

  public Media.ImageFormat getImageFormat() {
    return Media.ImageFormat.valueOf(getString(ImageFormatKey));
  }

  public void setImageFormat(Media.ImageFormat value) {
    setString(ImageFormatKey, String.valueOf(value));
  }

  public CameraView.DisplayImplementation getDisplayImplementation() {
    return CameraView.DisplayImplementation.valueOf(getString(DisplayImplementationKey));
  }

  public void setDisplayImplementation(CameraView.DisplayImplementation value) {
    setString(DisplayImplementationKey, String.valueOf(value));
  }

  public String getUser() {
    return getString(UserKey);
  }

  public void setUser(String value) {
    setString(UserKey, value);
  }

  public String getPassword() {
    return getString(PasswordKey);
  }

  public void setPassword(String value) {
    setString(PasswordKey, value);
  }

  public void setSelectedCameras(List<String> cameraNames) {
    setString(SelectedCamerasKey, String.join(",", cameraNames));
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
}
