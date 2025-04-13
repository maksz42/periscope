package com.maksz42.periscope.helper;

import android.graphics.Bitmap;

import java.lang.ref.SoftReference;

// TODO allow multiple bitmaps
// for seamless screen rotation etc.
// Any nice way to limit the scope??
public final class FrameHolder {
  private static SoftReference<Bitmap> bitmapRef;

  private FrameHolder() { }

  public static void set(Bitmap bitmap) {
    bitmapRef = new SoftReference<>(bitmap);
  }

  public static Bitmap getAndClear() {
    if (bitmapRef == null) {
      return null;
    }
    Bitmap bitmap = bitmapRef.get();
    bitmapRef = null;
    return bitmap;
  }
}
