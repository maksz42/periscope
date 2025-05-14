package com.maksz42.periscope.buffering;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

class FastBIS extends BufferedInputStream {
  private final int minimumMark;

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

  public boolean tryReset() {
    try {
      super.reset();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}