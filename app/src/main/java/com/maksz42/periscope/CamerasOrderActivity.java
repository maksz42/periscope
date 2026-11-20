package com.maksz42.periscope;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.maksz42.periscope.frigate.Config;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.ui.CameraArrayAdapter;
import com.maksz42.periscope.ui.CameraArrayAdapter.CameraItem;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CamerasOrderActivity extends Activity {
  /**
   * Boolean extra: choose the cameras of the "Watch over TV" list instead of the grid's. It
   * starts from what the list shows now and has no audio switches.
   */
  public static final String EXTRA_OVERLAY_LIST = "overlay_list";

  private boolean overlayList;
  private boolean refreshing;
  private final Set<String> usedCameraNames = new HashSet<>();
  private final List<CameraItem> cameraItems = new ArrayList<>();
  private CameraArrayAdapter cameraArrayAdapter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    super.onCreate(savedInstanceState);
    Settings settings = Settings.getInstance();
    overlayList = getIntent().getBooleanExtra(EXTRA_OVERLAY_LIST, false);
    cameraArrayAdapter = new CameraArrayAdapter(this, cameraItems,
        !overlayList && settings.getRtspEnabled());
    List<String> loadedCameras = overlayList
        ? settings.getOverlayCameras() != null ? settings.getOverlayCameras() : settings.getSelectedCameras()
        : settings.getSelectedCameras();
    // With no selection at all, the list offers every camera, so the picker starts with all ticked.
    boolean newOnesTicked = overlayList && loadedCameras == null;
    if (loadedCameras != null) {
      addCameraItems(loadedCameras, true);
    }
    ListView listView = new ListView(this);
    if (!overlayList) {
      listView.setOnItemLongClickListener((parent, view, position, id) -> view.findViewById(R.id.btn_audio).performClick());
    }
    listView.setOnItemClickListener((parent, view, position, id) -> view.findViewById(R.id.camera_enabled).performClick());
    listView.setOnKeyListener((v, keyCode, event) -> {
      if (event.getAction() != KeyEvent.ACTION_DOWN) {
        return false;
      }
      ListView lv = (ListView) v;
      View selectedView = lv.getSelectedView();
      if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
        lv.setSelection(lv.getSelectedItemPosition() - 1);
        selectedView.findViewById(R.id.btn_up).performClick();
      } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
        lv.setSelection(lv.getSelectedItemPosition() + 1);
        selectedView.findViewById(R.id.btn_down).performClick();
      }
      return false;
    });
    listView.setAdapter(cameraArrayAdapter);
    setContentView(listView);

    refreshing = true;
    new Thread(() -> {
      Config config = new Config();
      while (refreshing) {
        try {
          List<String> cameraNames = config.getCameras();
          runOnUiThread(() -> addCameraItems(cameraNames, newOnesTicked));
          break;
        } catch (Exception e) {
          Log.e(CamerasOrderActivity.this.getClass().getName(), "Error getting cameras", e);
          try {
            Thread.sleep(500);
          } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
          }
        }
      }
    }).start();

    getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_settings_title_bar);
    TextView title = findViewById(R.id.title);
    title.setText(overlayList ? R.string.overlay_cameras : R.string.cameras_order_activity_label);
    findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
  }

  private void addCameraItems(List<String> cameraNames, boolean enabled) {
    List<String> audioCameras = Settings.getInstance().getAudioCameras();
    for (String cameraName : cameraNames) {
      if (usedCameraNames.add(cameraName)) {
        cameraItems.add(new CameraItem(cameraName, enabled, audioCameras.contains(cameraName)));
      }
    }
    cameraArrayAdapter.notifyDataSetChanged();
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
    List<String> selectedCameras = new ArrayList<>(cameraItems.size());
    List<String> audioCameras = new ArrayList<>(cameraItems.size());
    for (CameraItem cameraItem : cameraItems) {
      if (cameraItem.enabled) {
        selectedCameras.add(cameraItem.name);
      }
      if (cameraItem.audioEnabled) {
        audioCameras.add(cameraItem.name);
      }
    }
    if (overlayList) {
      Settings.getInstance().setOverlayCameras(selectedCameras).apply();
      return;
    }
    Settings.getInstance()
        .setSelectedCameras(selectedCameras)
        .setAudioCameras(audioCameras)
        .apply();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    refreshing = false;
  }
}
