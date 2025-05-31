package com.maksz42.periscope.ui.cameradisplayimpl;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.ui.CameraDisplay;
import com.maksz42.periscope.utils.Graphics;

public class BitmapDisplay extends View implements CameraDisplay {
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final FrameBuffer frameBuffer;
  private final boolean ignoreAspectRatio;
  private final Rect dstRect = new Rect();
  private final Rect blackBarLeftTop = new Rect();
  private final Rect blackBarRightBottom = new Rect();

  {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.BLACK);
  }

  public BitmapDisplay(Context context, boolean ignoreAspectRatio, FrameBuffer buffer) {
    super(context);
    this.ignoreAspectRatio = ignoreAspectRatio;
    this.frameBuffer = (buffer != null) ? buffer : FrameBuffer.newNonBlockingFrameBuffer();
  }

  @Override
  public void requestDraw() {
    postInvalidate();
  }

  @Override
  public FrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    frameBuffer.lock();
    try {
      Bitmap bitmap = frameBuffer.getFrame();
      if (bitmap == null) {
        canvas.drawColor(Color.BLACK);
        return;
      }
      int vw = getWidth();
      int vh = getHeight();
      if (ignoreAspectRatio) {
        dstRect.right = vw;
        dstRect.bottom = vh;
      } else {
        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        Graphics.scaleRectKeepRatio(
            bw, bh, vw, vh, dstRect, blackBarLeftTop, blackBarRightBottom
        );
      }
      canvas.drawBitmap(bitmap, null, dstRect, paint);
      canvas.drawRect(blackBarLeftTop, paint);
      canvas.drawRect(blackBarRightBottom, paint);
    } finally {
      frameBuffer.unlock();
    }
  }
}