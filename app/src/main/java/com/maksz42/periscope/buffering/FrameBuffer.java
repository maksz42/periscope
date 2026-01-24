package com.maksz42.periscope.buffering;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB;
import static android.os.Build.VERSION_CODES.KITKAT;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class FrameBuffer {
  // Although WEBP is supported from ICE_CREAM_SANDWICH
  // BitmapFactory.Options#inBitmap supports WEBP
  // for api >= KITKAT
  public static final boolean SUPPORTS_WEBP = (SDK_INT >= KITKAT);
  static final boolean SUPPORTS_REUSING_BITMAP = (SDK_INT >= HONEYCOMB);
  private static final boolean HAS_NATIVE_STREAM_BUFFER = (SDK_INT >= KITKAT);
  private static final boolean NEEDS_SIGSEGV_MITIGATION = (SDK_INT == KITKAT);

  protected final Lock lock = new ReentrantLock();
  private final BitmapFactory.Options bitmapFactoryOptions;
  private final byte[] streamBuffer = new byte[16 * 1024];


  {
    bitmapFactoryOptions = new BitmapFactory.Options();
    if (SUPPORTS_REUSING_BITMAP) {
      bitmapFactoryOptions.inMutable = true;
      // https://stackoverflow.com/questions/16034756
      bitmapFactoryOptions.inSampleSize = 1;
    }
    bitmapFactoryOptions.inTempStorage = new byte[16 * 1024];
  }

  public static FrameBuffer newNonBlockingFrameBuffer() {
    return FrameBuffer.SUPPORTS_REUSING_BITMAP
        ? new DoubleFrameBuffer()
        : new SingleFrameBuffer(false);
  }

  public void lock() {
    lock.lock();
  }

  public void unlock() {
    lock.unlock();
  }

  public abstract void decodeStream(InputStream input) throws IOException;

  public abstract Bitmap getFrame();

  final protected Bitmap decodeStream(InputStream input, Bitmap reusableBitmap) throws IOException {
    FastBIS fastBIS = NEEDS_SIGSEGV_MITIGATION
        ? new CatchingFastBIS(input, streamBuffer)
        : new FastBIS(input, streamBuffer);
    return decodeStream(fastBIS, reusableBitmap);
  }

  private Bitmap decodeStream(FastBIS input, Bitmap reusableBitmap) throws IOException {
    if (SUPPORTS_REUSING_BITMAP) {
      bitmapFactoryOptions.inBitmap = reusableBitmap;
      if (HAS_NATIVE_STREAM_BUFFER) {
        // mark() could be called unconditionally but it's synchronized, so not free
        input.mark(streamBuffer.length);
      }
    }
    Bitmap bitmap = null;
    try {
      bitmap = BitmapFactory.decodeStream(input, null, bitmapFactoryOptions);
    } catch (IllegalArgumentException e) {
      if (SUPPORTS_REUSING_BITMAP && input.tryReset()) {
        bitmapFactoryOptions.inBitmap = null;
        bitmap = BitmapFactory.decodeStream(input, null, bitmapFactoryOptions);
      }
    }
    if (bitmap == null) {
      throw new IOException("Failed to decode image");
    }
    return bitmap;
  }
}
