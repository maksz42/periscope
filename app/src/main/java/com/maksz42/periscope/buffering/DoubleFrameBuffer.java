package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.InputStream;

public class DoubleFrameBuffer extends FrameBuffer {
  private volatile Bitmap frontBuffer;
  private Bitmap backBuffer;

  @Override
  public void decodeStream(InputStream input) throws IOException {
    backBuffer = decodeStream(input, backBuffer);
    swapBuffers();
    onUpdate();
  }

  private void swapBuffers() {
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
    frontBuffer = bitmap;
    onUpdate();
  }
}
