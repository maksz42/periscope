package com.maksz42.periscope.helper;

import android.util.Log;

import com.maksz42.periscope.camera.CameraPlayer;

public final class CameraPlayerHolder {
  private static final String TAG = "CameraPlayerHolder";
  private static CameraPlayer player;

  private CameraPlayerHolder() { }

  public static void set(CameraPlayer cameraPlayer) {
    if (player != null) {
      Log.e(TAG, "CameraPlayer not consumed");
      player.shutdown();
    }
    player = cameraPlayer;
  }

  public static CameraPlayer getAndClear(String cameraName) {
    CameraPlayer p = player;
    if (p == null) return null;
    player = null;
    if (p.isShutdown()) {
      Log.d(TAG, "Player is shutdown");
      return null;
    }
    if (!p.getMedia().getName().equals(cameraName)) {
      Log.d(TAG, "Player mismatch");
      p.shutdown();
      return null;
    }
    return p;
  }
}
