package com.maksz42.periscope.buffering;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.RequiresApi;

public abstract class FrameBuffer {
  public interface OnFrameUpdateListener {
    void onFrameUpdate();
  }

  @RequiresApi(HONEYCOMB)
  protected static BitmapFactory.Options createReusableBitmapOptions(Bitmap reusableBitmap) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inMutable = true;
    options.inBitmap = reusableBitmap;
    // https://stackoverflow.com/questions/16034756
    options.inSampleSize = 1;
    return options;
  }

  // Although WEBP is supported from ICE_CREAM_SANDWICH
  // BitmapFactory.Options#inBitmap supports WEBP
  // for api >= KITKAT
  public static boolean supportsInBitmap() {
    return SDK_INT >= HONEYCOMB;
  }

  protected OnFrameUpdateListener onFrameUpdateListener;

  @RequiresApi(HONEYCOMB)
  public abstract void decodeByteArray(byte[] data);

  public abstract Bitmap getFrame();

  public abstract void setFrame(Bitmap bitmap);


  protected void onUpdate() {
    if (onFrameUpdateListener != null) {
      onFrameUpdateListener.onFrameUpdate();
    }
  }

  protected Bitmap internalDecodeByteArray(byte[] data, BitmapFactory.Options options) {
    try {
      return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    } catch (IllegalArgumentException e) {
      return BitmapFactory.decodeByteArray(data, 0, data.length);
    }
  }

  public void setOnFrameUpdateListener(OnFrameUpdateListener onFrameUpdateListener) {
    this.onFrameUpdateListener = onFrameUpdateListener;
  }
}
