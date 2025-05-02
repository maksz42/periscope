package com.maksz42.periscope.buffering;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB;
import static android.os.Build.VERSION_CODES.KITKAT;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.io.RetryInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class FrameBuffer {
  public interface OnFrameUpdateListener {
    void onFrameUpdate();
  }

  private final Lock lock = supportsReusingBitmap() ? new ReentrantLock() : null;

  public void lock() {
    if (lock != null) {
      lock.lock();
    }
  }

  public void lockInterruptibly() throws InterruptedException {
    if (lock != null) {
      lock.lockInterruptibly();
    }
  }

  public void unlock() {
    if (lock != null) {
      lock.unlock();
    }
  }

  @RequiresApi(HONEYCOMB)
  private static BitmapFactory.Options createReusableBitmapOptions(Bitmap reusableBitmap) {
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
  public static boolean supportsWEBP() {
    return SDK_INT >= KITKAT;
  }

  public static boolean supportsReusingBitmap() {
    return SDK_INT >= HONEYCOMB;
  }

  protected OnFrameUpdateListener onFrameUpdateListener;

  public abstract void decodeStream(InputStream input) throws IOException;

  public abstract Bitmap getFrame();

  public abstract void setFrame(Bitmap bitmap);


  protected void onUpdate() {
    if (onFrameUpdateListener != null) {
      onFrameUpdateListener.onFrameUpdate();
    }
  }

  final protected Bitmap decodeStream(InputStream input, Bitmap reusableBitmap) throws IOException {
    boolean canReuseBitmaps = supportsReusingBitmap();
    BitmapFactory.Options opts = null;
    if (canReuseBitmaps) {
      opts = createReusableBitmapOptions(reusableBitmap);
      // hopefully 8kB is enough
      input = new RetryInputStream(input, 8 * 1024);
    }
    Bitmap bitmap = null;
    try {
      bitmap = BitmapFactory.decodeStream(input, null, opts);
    } catch (IllegalArgumentException e) {
      if (canReuseBitmaps) {
        input.reset();
        opts.inBitmap = null;
        bitmap = BitmapFactory.decodeStream(input, null, opts);
      }
    }
    if (bitmap == null) {
      throw new IOException("Failed to decode image");
    }
    return bitmap;
  }

  public void setOnFrameUpdateListener(OnFrameUpdateListener onFrameUpdateListener) {
    this.onFrameUpdateListener = onFrameUpdateListener;
  }
}
