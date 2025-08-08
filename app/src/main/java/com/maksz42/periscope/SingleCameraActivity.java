package com.maksz42.periscope;

import android.content.Intent;
import android.os.Bundle;

import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.helper.CameraPlayerHolder;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.ui.CameraView;

public class SingleCameraActivity extends AbstractPreviewActivity {
  private CameraView cameraView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addFloatingButton(android.R.drawable.ic_dialog_dialer, v -> {
      cachePlayer();
      startActivity(new Intent(this, MatrixActivity.class));
    });
  }

  @Override
  protected void onStart() {
    super.onStart();
    Settings settings = Settings.getInstance(this);
    boolean ignoreAspectRatio = settings.getIgnoreAspectRatio();
    short timeout = settings.getTimeout();
    Media.ImageFormat imageFormat = settings.getImageFormat();
    CameraView.DisplayImplementation displayImplementation = settings.getDisplayImplementation();
    String cameraName = getIntent().getStringExtra("camera_name");
    Media media = new Media(cameraName, imageFormat, 80);
    cameraView = new CameraView(
        this,
        media,
        displayImplementation,
        ignoreAspectRatio,
        true,
        timeout,
        CameraPlayerHolder.getAndClear(cameraName)
    );
    cameraView.setOnErrorListener(cameraViewErrorListener);
    showWallpaperMsg(null);
    setPreview(cameraView);
    cameraView.start(0, 100);

    checkForUpdates(2_000);
  }

  @Override
  protected void onStop() {
    super.onStop();
    cameraView.stop();
  }

  @Override
  protected void onAppUIVisibilityChange(boolean visible) {
    cameraView.setCameraNameVisible(visible);
  }

  private void cachePlayer() {
    CameraPlayerHolder.set(cameraView.detachPlayer());
  }

  @Override
  public void onBackPressed() {
    cachePlayer();
    super.onBackPressed();
  }
}
