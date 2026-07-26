package com.maksz42.periscope.buffering;

import android.util.Log;

import com.maksz42.periscope.utils.UnsynchronizedBufferedInputStream;

import java.io.IOException;
import java.io.InputStream;

class FastBIS extends UnsynchronizedBufferedInputStream {
  private static final String TAG = "FastBIS";

  boolean exceptionThrown;

  FastBIS() {
    super(null, 16 * 1024);
  }

  void begin(InputStream input) {
    in = input;
    count = 0;
    pos = 0;
    markpos = 0;
    marklimit = buf.length;
    exceptionThrown = false;
  }

  @Override
  public void mark(int readlimit) { }

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

  boolean tryReset() {
    if (buf == null || markpos == -1) return false;
    pos = markpos;
    return true;
  }
}