package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.InputStream;

public class SingleFrameBuffer extends FrameBuffer {
  private volatile Bitmap bitmap;

  @Override
  public void decodeStream(InputStream input) throws IOException {
    lock();
    try {
      bitmap = decodeStream(input, bitmap);
    } finally {
      unlock();
    }
    onUpdate();
  }

  @Override
  public Bitmap getFrame() {
    return bitmap;
  }

  @Override
  public void setFrame(Bitmap bitmap) {
    this.bitmap = bitmap;
    onUpdate();
  }
}
