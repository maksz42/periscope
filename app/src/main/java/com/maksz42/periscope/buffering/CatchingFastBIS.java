package com.maksz42.periscope.buffering;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

class CatchingFastBIS extends FastBIS {
  CatchingFastBIS(InputStream in, byte[] buf) {
    super(in, buf);
  }

  @Override
  public int read(byte[] b, int off, int len) {
    try {
      return super.read(b, off, len);
    } catch (IOException e) {
      // primarily SocketTimeoutException
      Log.d(
          "CatchingFastBIS",
          "Swallowed " + e.getClass().getSimpleName() + ", returning EOF",
          e
      );
      return -1;
    }
  }
}
