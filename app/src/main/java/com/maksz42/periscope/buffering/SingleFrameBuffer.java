package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.InputStream;

public class SingleFrameBuffer extends FrameBuffer {
  private volatile Bitmap bitmap;

  @Override
  public void decodeStream(InputStream input) throws IOException {
    lockIfSupportsReusingBitmaps();
    try {
      Bitmap oldBitmap = bitmap;
      bitmap = decodeStream(input, oldBitmap);
      recycleBitmapIfNeeded(oldBitmap);
    } finally {
      unlockIfSupportsReusingBitmaps();
    }
    onUpdate();
  }

  @Override
  public Bitmap getFrame() {
    return bitmap;
  }

  @Override
  public Bitmap getFrameCopy() {
    if (bitmap == null) {
      return null;
    }
    lock();
    try {
      return bitmap.copy(bitmap.getConfig(), SUPPORTS_REUSING_BITMAP);
    } finally {
      unlock();
    }
  }

  @Override
  public void setFrame(Bitmap bitmap) {
    this.bitmap = bitmap;
    onUpdate();
  }

  // TODO find a better way to recycle bitmaps
  private void recycleBitmapIfNeeded(Bitmap bitmap) {
    // https://developer.android.com/topic/performance/graphics/manage-memory#recycle
    if (SUPPORTS_REUSING_BITMAP || bitmap == null) return;
    // api <= 10
    lock();
    try {
      bitmap.recycle();
    } finally {
      unlock();
    }
  }
}
