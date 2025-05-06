package com.maksz42.periscope;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.maksz42.periscope.frigate.Config;
import com.maksz42.periscope.frigate.InvalidCredentialsException;
import com.maksz42.periscope.frigate.InvalidResponseException;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.helper.FrameHolder;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.ui.CameraView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

public class MatrixActivity extends AbstractPreviewActivity {
  private List<CameraView> cameraViews;
  private volatile boolean refreshing;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addFloatingButton(android.R.drawable.ic_menu_sort_by_size, CamerasOrderActivity.class);
    checkForUpdates(10_000);
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    checkForUpdates(4_000);
  }

  @Override
  protected void onStart() {
    super.onStart();
    List<String> selectedCameras = Settings.getInstance(this).getSelectedCameras();
    if (selectedCameras == null) {
      refreshing = true;
      new Thread(() -> {
        Config config = new Config();
        while (refreshing) {
          try {
            List<String> cameraNames = config.getCameras();
            runOnUiThread(() -> {
              showWallpaperMsg(null);
              initCameraViews(cameraNames.subList(0, Math.min(4, cameraNames.size())));
              addCameraViews();
              startPreview();
            });
            break;
          } catch (InvalidCredentialsException | SSLException e) {
            handleCommonErrors(e);
            return;
          } catch (IOException | InterruptedException | InvalidResponseException e) {
            Log.e(MatrixActivity.this.getClass().getName(), "Error getting cameras", e);
            runOnUiThread(
                () -> showWallpaperMsg(getString(R.string.error_getting_cameras, Client.getBaseUrl()))
            );
            try {
              Thread.sleep(500);
            } catch (InterruptedException ex) {
              throw new RuntimeException(ex);
            }
          }
        }
      }).start();
    } else if (selectedCameras.isEmpty()) {
      showWallpaperMsg(getString(R.string.no_cameras_selected));
    } else {
      initCameraViews(selectedCameras);
      addCameraViews();
      startPreview();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    refreshing = false;
    stopPreview();
    removeCameraViews();
    cameraViews = null;
  }

  @Override
  protected void onAppUIVisibilityChange(boolean visible) {
    if (cameraViews == null) return;
    for (CameraView cameraView : cameraViews) {
      cameraView.setCameraNameVisible(visible);
    }
  }

  private void addCameraViews() {
    if (cameraViews == null) return;
    LinearLayout matrixLayout = new LinearLayout(this);
    matrixLayout.setOrientation(LinearLayout.VERTICAL);
    int numberOfCameras = cameraViews.size();
    int numberOfRows = (int) Math.floor(Math.sqrt(numberOfCameras));
    int numberOfColumns = (int) Math.ceil((double) numberOfCameras / numberOfRows);
    int orientation = getResources().getConfiguration().orientation;
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      int temp = numberOfRows;
      numberOfRows = numberOfColumns;
      numberOfColumns = temp;
    }
    LinearLayout.LayoutParams colParams =
        new LinearLayout.LayoutParams(0, MATCH_PARENT);
    colParams.weight = 1;
    LinearLayout.LayoutParams rwoParams =
        new LinearLayout.LayoutParams(MATCH_PARENT, 0);
    rwoParams.weight = 1;
    for (int i = 0; i < numberOfRows; i++) {
      LinearLayout row = new LinearLayout(this);
      for (int j = 0; j < numberOfColumns; j++) {
        int idx = i * numberOfColumns + j;
        if (idx >= numberOfCameras) {
          break;
        }
        CameraView view = cameraViews.get(idx);
        row.addView(view, colParams);
      }
      matrixLayout.addView(row, rwoParams);
    }
    setContentView(matrixLayout);
  }

  private void removeCameraViews() {
    setContentView(null);
    if (cameraViews == null) return;
    for (View cameraView : cameraViews) {
      ViewGroup parent = (ViewGroup) cameraView.getParent();
      parent.removeView(cameraView);
    }
  }

  private void initCameraViews(List<String> cameras) {
    List<CameraView> cameraViews = new ArrayList<>(cameras.size());
    Settings settings = Settings.getInstance(this);
    boolean ignoreAspectRatio = settings.getIgnoreAspectRatio();
    short timeout = settings.getTimeout();
    Media.ImageFormat imageFormat = settings.getImageFormat();
    CameraView.DisplayImplementation displayImplementation = settings.getDisplayImplementation();
    for (String cameraName : cameras) {
      Media media = new Media(cameraName, imageFormat, -1);
      Intent intent = new Intent(this, SingleCameraActivity.class);
      intent.putExtra("camera_name", cameraName);
      CameraView cameraView =
          new CameraView(this, media, displayImplementation, ignoreAspectRatio);
      cameraView.setOnClickListener(v -> {
        Bitmap bitmap = ((CameraView) v).getCameraDisplay()
            .getFrameBuffer()
            .getFrame();
        FrameHolder.set(bitmap);
        startActivity(intent);
      });
      cameraView.setTimeout(timeout);
      cameraView.setOnErrorListener(this::handleCommonErrors);
      cameraViews.add(cameraView);
    }
    this.cameraViews = cameraViews;
  }

  private void startPreview() {
    long interval = Settings.getInstance(this).getInterval();
    int numberOfCameras = cameraViews.size();
    long timeShift = interval / numberOfCameras;
    for (int i = 0; i < numberOfCameras; i++) {
      CameraView cameraView = cameraViews.get(i);
      cameraView.start(timeShift * i, interval);
    }
  }

  private void stopPreview() {
    if (cameraViews == null) return;
    for (CameraView cameraView : cameraViews) {
      cameraView.stop();
    }
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    removeCameraViews();
    addCameraViews();
  }

  @Override
  public void onBackPressed() { }
}
