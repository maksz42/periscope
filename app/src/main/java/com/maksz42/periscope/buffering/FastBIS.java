package com.maksz42.periscope.buffering;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

class FastBIS extends BufferedInputStream {
  private static final String TAG = "FastBIS";

  private final int minimumMark;
  public boolean exceptionThrown = false;

  FastBIS(InputStream in, byte[] buf) {
    // hack
    super(in, 1);
    this.minimumMark = buf.length;
    this.buf = buf;
  }

  @Override
  public void mark(int readlimit) {
    super.mark(Math.max(readlimit, minimumMark));
  }

  @Override
  public int read(byte[] b, int off, int len) {
    try {
      return super.read(b, off, len);
    } catch (IOException e) {
      // primarily SocketTimeoutException
      // also java.net.SocketException: Software caused connection abort
      Log.d(
          TAG,
          "Swallowed " + e.getClass().getSimpleName() + ", returning EOF",
          e
      );
      exceptionThrown = true;
      return -1;
    }
  }

  public boolean tryReset() {
    try {
      super.reset();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}