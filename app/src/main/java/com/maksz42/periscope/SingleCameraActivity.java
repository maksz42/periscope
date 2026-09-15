package com.maksz42.periscope;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.helper.CameraPlayerHolder;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.ui.CameraView;

public class SingleCameraActivity extends AbstractPreviewActivity {
  private CameraView cameraView;
  private ImageButton muteButton;

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

    String cameraName = getIntent().getStringExtra("camera_name");
    Settings settings = Settings.getInstance();
    boolean ignoreAspectRatio = settings.getIgnoreAspectRatio();
    short timeout = settings.getTimeout();
    Media.ImageFormat imageFormat = settings.getImageFormat();
    CameraView.DisplayImplementation displayImplementation = settings.getDisplayImplementation();
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

    if (settings.getRtspEnabled()) {
      startAudio(cameraView);
      if (muteButton == null) {
        muteButton = addFloatingButton(android.R.drawable.ic_lock_silent_mode_off, v -> {
          int drawableRes;
          if (!cameraView.getPlayer().hasAudio()) {
            startAudio(cameraView);
            drawableRes = android.R.drawable.ic_lock_silent_mode_off;
          } else {
            stopAudio(cameraView);
            drawableRes = android.R.drawable.ic_lock_silent_mode;
          }
          ImageButton btn = (ImageButton) v;
          btn.setImageResource(drawableRes);
        });
      }
    } else if (muteButton != null) {
      removeFloatingButton(muteButton);
      muteButton = null;
    }

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
