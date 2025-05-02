package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LazyBitmapDrawable extends Drawable {
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final FrameBuffer frameBuffer = FrameBuffer.supportsReusingBitmap()
      ? new DoubleFrameBuffer()
      : new SingleFrameBuffer();


  public FrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    frameBuffer.lock();
    try {
      Bitmap bitmap = frameBuffer.getFrame();
      if (bitmap != null) {
        canvas.drawBitmap(bitmap, null, getBounds(), paint);
      }
    } finally {
      frameBuffer.unlock();
    }
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {
    paint.setColorFilter(colorFilter);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.OPAQUE;
  }
}
