package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class SingleFrameBuffer extends FrameBuffer {
  private volatile Bitmap bitmap;

  @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  public void decodeByteArray(byte[] data) {
    synchronized (this) {
      BitmapFactory.Options options = createReusableBitmapOptions(bitmap);
      bitmap = internalDecodeByteArray(data, options);
    }
    onUpdate();
  }

  @Override
  public Bitmap getFrame() {
    return bitmap;
  }

  @Override
  public void setFrame(Bitmap bitmap) {
    synchronized (this) {
      this.bitmap = bitmap;
    }
    onUpdate();
  }
}
