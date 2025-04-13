package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class DoubleFrameBuffer extends FrameBuffer {
  private volatile Bitmap frontBuffer;
  private volatile Bitmap backBuffer;


  @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  public void decodeByteArray(byte[] data) {
    BitmapFactory.Options options = createReusableBitmapOptions(backBuffer);
    Bitmap bitmap = internalDecodeByteArray(data, options);
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
