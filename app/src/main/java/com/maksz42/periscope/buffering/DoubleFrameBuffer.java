package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.InputStream;

public class DoubleFrameBuffer extends FrameBuffer {
  private volatile Bitmap frontBuffer;
  private volatile Bitmap backBuffer;

  @Override
  public void decodeStream(InputStream input) throws IOException {
    Bitmap bitmap = decodeStream(input, backBuffer);
    setFrame(bitmap);
  }

  // TODO make sure this synchronization is enough
  private synchronized void swapBuffers() {
    Bitmap temp = frontBuffer;
    frontBuffer = backBuffer;
    backBuffer = temp;
  }

  @Override
  public Bitmap getFrame() {
    return frontBuffer;
  }

  @Override
  public void setFrame(Bitmap bitmap) {
    backBuffer = bitmap;
    swapBuffers();
    onUpdate();
  }
}
