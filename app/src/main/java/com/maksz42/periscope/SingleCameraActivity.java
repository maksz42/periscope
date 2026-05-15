package com.maksz42.periscope;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import com.maksz42.periscope.camera.rtsp.RtspClient;
import com.maksz42.periscope.frigate.Client;
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

    String cameraName = getIntent().getStringExtra("camera_name");
    if (settings.getRtspEnabled() && muteButton == null) {
      muteButton = addFloatingButton(android.R.drawable.ic_lock_silent_mode_off, v -> {
        int drawableRes;
        if (rtspClient == null) {
          startAudio(settings, cameraName);
          drawableRes = android.R.drawable.ic_lock_silent_mode_off;
        } else {
          stopAudio();
          drawableRes = android.R.drawable.ic_lock_silent_mode;
        }
        ImageButton btn = (ImageButton) v;
        btn.setImageResource(drawableRes);
      });
    } else if (!settings.getRtspEnabled() && muteButton != null) {
      removeFloatingButton(muteButton);
      muteButton = null;
    }

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

    checkForUpdates(2_000);

    if (settings.getRtspEnabled()) {
      startAudio(settings, cameraName);
    }
  }

  private void startAudio(Settings settings, String cameraName) {
    rtspClient = new RtspClient(
        settings.getHost(),
        settings.getRtspPort(),
        settings.getRtspUser(),
        settings.getRtspPassword(),
        cameraName,
        settings.getProtocol() == Client.Protocol.HTTPS
    );
    rtspClient.start();
  }

  private void stopAudio() {
    if (rtspClient != null) {
      rtspClient.stop();
      rtspClient = null;
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    cameraView.stop();
    stopAudio();
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
