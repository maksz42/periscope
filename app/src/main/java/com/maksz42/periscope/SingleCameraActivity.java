package com.maksz42.periscope;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ImageButton;

import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.helper.CameraPlayerHolder;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.ui.CameraView;

import java.util.List;

public class SingleCameraActivity extends AbstractPreviewActivity {
  private CameraView cameraView;
  private ImageButton muteButton;
  private float swipeDownX;
  private float swipeDownY;
  private int swipeActivityWidth;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addFloatingButton(android.R.drawable.ic_dialog_dialer, v -> {
      cachePlayer();
      startActivity(new Intent(this, MatrixActivity.class));
    });
  }

  private String getAdjacentCamera(int direction) {
    String cameraName = cameraView.getMedia().getName();
    List<String> cameras = Settings.getInstance().getSelectedCameras();
    int idx = cameras.indexOf(cameraName);
    if (idx < 0) return null;
    return cameras.get((idx + direction + cameras.size()) % cameras.size());
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN -> {
        swipeDownX = event.getX();
        swipeDownY = event.getY();
        swipeActivityWidth = getWindow().getDecorView().getWidth();
      }
      case MotionEvent.ACTION_UP -> {
        int width = swipeActivityWidth;
        swipeActivityWidth = 0;
        if (width == 0) break;
        float dx = event.getX() - swipeDownX;
        float dy = event.getY() - swipeDownY;

        if (Math.abs(dy) > Math.abs(dx)) break;
        if (Math.abs(dx) < (width >> 2)) break;

        String adjacentCamera = getAdjacentCamera((dx > 0) ? -1 : 1);
        if (adjacentCamera == null) break;
        boolean audio = cameraView.getPlayer().hasAudio();
        cameraView.stop();
        startCamera(adjacentCamera);
        if (audio) {
          startAudio(cameraView);
        }
      }
      case MotionEvent.ACTION_CANCEL -> swipeActivityWidth = 0;
    }
    return true;
  }

  private void startCamera(String cameraName) {
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
  }

  @Override
  protected void onStart() {
    super.onStart();

    String cameraName = getIntent().getStringExtra("camera_name");
    startCamera(cameraName);

    Settings settings = Settings.getInstance();
    if (settings.getRtspEnabled()) {
      boolean audioEnabled = settings.getAudioEnabledByDefault();
      int initDrawableId;
      if (audioEnabled) {
        startAudio(cameraView);
        initDrawableId = android.R.drawable.ic_lock_silent_mode_off;
      } else {
        stopAudio(cameraView);
        initDrawableId = android.R.drawable.ic_lock_silent_mode;
      }
      if (muteButton == null) {
        muteButton = addFloatingButton(initDrawableId, v -> {
          int drawableRes;
          if (cameraView.getPlayer().hasAudio()) {
            stopAudio(cameraView);
            drawableRes = android.R.drawable.ic_lock_silent_mode;
          } else {
            startAudio(cameraView);
            drawableRes = android.R.drawable.ic_lock_silent_mode_off;
          }
          ImageButton btn = (ImageButton) v;
          btn.setImageResource(drawableRes);
        });
      } else {
        muteButton.setImageResource(initDrawableId);
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
