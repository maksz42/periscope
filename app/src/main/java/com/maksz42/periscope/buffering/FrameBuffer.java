package com.maksz42.periscope.buffering;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.GINGERBREAD_MR1;
import static android.os.Build.VERSION_CODES.HONEYCOMB;
import static android.os.Build.VERSION_CODES.KITKAT;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class FrameBuffer {
  private static class FastBIS extends BufferedInputStream {
    private final int minimumMark;

    public FastBIS(InputStream in, byte[] buf) {
      // hack
      super(in, 1);
      this.minimumMark = buf.length;
      this.buf = buf;
    }

    @Override
    public void mark(int readlimit) {
      super.mark(Math.max(readlimit, minimumMark));
    }

    @Override
    public void reset() {
      try {
        super.reset();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public interface OnFrameUpdateListener {
    void onFrameUpdate();
  }

  // Although WEBP is supported from ICE_CREAM_SANDWICH
  // BitmapFactory.Options#inBitmap supports WEBP
  // for api >= KITKAT
  public static final boolean SUPPORTS_WEBP = (SDK_INT >= KITKAT);
  static final boolean SUPPORTS_REUSING_BITMAP = (SDK_INT >= HONEYCOMB);
  private static final boolean HAS_NATIVE_STREAM_BUFFER = (SDK_INT >= KITKAT);
  private static final boolean SHOULD_RECYCLE_BITMAP = (SDK_INT <= GINGERBREAD_MR1);

  private final Lock lock = SUPPORTS_REUSING_BITMAP ? new ReentrantLock() : null;
  private final byte[] tempStorage = new byte[16 * 1024];
  private final byte[] streamBuffer = new byte[16 * 1024];


  public static FrameBuffer newNonBlockingFrameBuffer() {
    return FrameBuffer.SUPPORTS_REUSING_BITMAP
        ? new DoubleFrameBuffer()
        : new SingleFrameBuffer();
  }

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
  private BitmapFactory.Options createReusableBitmapOptions(Bitmap reusableBitmap) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inMutable = true;
    options.inBitmap = reusableBitmap;
    options.inTempStorage = tempStorage;
    // https://stackoverflow.com/questions/16034756
    options.inSampleSize = 1;
    return options;
  }

  protected OnFrameUpdateListener onFrameUpdateListener;

  public abstract void decodeStream(InputStream input) throws IOException;

  public abstract Bitmap getFrame();

  public abstract Bitmap getFrameCopy();

  public abstract void setFrame(Bitmap bitmap);


  protected void onUpdate() {
    if (onFrameUpdateListener != null) {
      onFrameUpdateListener.onFrameUpdate();
    }
  }

  final protected Bitmap decodeStream(InputStream input, Bitmap reusableBitmap) throws IOException {
    BitmapFactory.Options opts = null;
    input = new FastBIS(input, streamBuffer);
    if (SUPPORTS_REUSING_BITMAP) {
      opts = createReusableBitmapOptions(reusableBitmap);
      // mark() could be called unconditionally but it's synchronized,
      // so not free
      if (HAS_NATIVE_STREAM_BUFFER) {
        input.mark(streamBuffer.length);
      }
    }
    Bitmap bitmap = null;
    try {
      bitmap = BitmapFactory.decodeStream(input, null, opts);
    } catch (IllegalArgumentException e) {
      if (SUPPORTS_REUSING_BITMAP) {
        input.reset();
        opts.inBitmap = null;
        bitmap = BitmapFactory.decodeStream(input, null, opts);
      }
    }
    if (bitmap == null) {
      throw new IOException("Failed to decode image");
    }
    // https://developer.android.com/topic/performance/graphics/manage-memory#recycle
    if (SHOULD_RECYCLE_BITMAP && reusableBitmap != null) {
      reusableBitmap.recycle();
    }
    return bitmap;
  }

  public void setOnFrameUpdateListener(OnFrameUpdateListener onFrameUpdateListener) {
    this.onFrameUpdateListener = onFrameUpdateListener;
  }
}
