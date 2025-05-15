package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.InputStream;

class DoubleFrameBuffer extends FrameBuffer {
  private volatile Bitmap frontBuffer;
  private Bitmap backBuffer;

  @Override
  public void decodeStream(InputStream input) throws IOException {
    backBuffer = decodeStream(input, backBuffer);
    swapBuffers();
  }

  private void swapBuffers() {
    lock();
    try {
      Bitmap temp = frontBuffer;
      frontBuffer = backBuffer;
      backBuffer = temp;
    } finally {
      unlock();
    }
  }

  @Override
  public Bitmap getFrame() {
    return frontBuffer;
  }

  @Override
  public void setFrame(Bitmap bitmap) {
    frontBuffer = bitmap;
  }
}
