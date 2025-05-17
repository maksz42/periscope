package com.maksz42.periscope.helper;

import android.util.Log;

import com.maksz42.periscope.camera.CameraPlayer;

public final class CameraPlayerHolder {
  private static CameraPlayer player;

  private CameraPlayerHolder() { }

  public static void set(CameraPlayer cameraPlayer) {
    if (player != null) {
      Log.e("CameraPlayerHolder", "CameraPlayer not consumed");
      player.shutdown();
    }
    player = cameraPlayer;
  }

  public static CameraPlayer getAndClear() {
    CameraPlayer p = player;
    player = null;
    return p;
  }
}
