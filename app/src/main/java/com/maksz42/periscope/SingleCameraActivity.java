package com.maksz42.periscope;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import com.maksz42.periscope.camera.rtsp.RtspClient;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.helper.CameraPlayerHolder;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.ui.CameraView;

public class SingleCameraActivity extends AbstractPreviewActivity {
  private CameraView cameraView;
  private RtspClient rtspClient;
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
    Settings settings = Settings.getInstance(this);

    if (settings.getRtspEnabled() && muteButton == null) {
      muteButton = addFloatingButton(android.R.drawable.ic_lock_silent_mode_off, v -> {
        if (rtspClient == null) return;
        boolean muted = rtspClient.getMuted();
        rtspClient.setMuted(!muted);
        ImageButton btn = (ImageButton) v;
        btn.setImageResource(
            muted ? android.R.drawable.ic_lock_silent_mode_off : android.R.drawable.ic_lock_silent_mode
        );
      });
    } else if (!settings.getRtspEnabled() && muteButton != null) {
      removeFloatingButton(muteButton);
      muteButton = null;
    }

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

    if (settings.getRtspEnabled()) {
      rtspClient = new RtspClient(
          settings.getHost(),
          settings.getRtspPort(),
          settings.getRtspUser(),
          settings.getRtspPassword(),
          cameraName
      );
      rtspClient.start();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    cameraView.stop();
    if (rtspClient != null) {
      rtspClient.stop();
      rtspClient = null;
    }
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
