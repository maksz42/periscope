package com.maksz42.periscope.ui.cameradisplayimpl;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;

import com.maksz42.periscope.Periscope;
import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.ui.CameraDisplay;
import com.maksz42.periscope.utils.Graphics;

public class BitmapDisplay extends View implements CameraDisplay {
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final FrameBuffer frameBuffer;
  private final boolean ignoreAspectRatio;
  private final boolean drawLetterboxHW;
  private final Rect dstRect = new Rect();
  private final Rect blackBarLeftTop;
  private final Rect blackBarRightBottom;


  public BitmapDisplay(Context context, boolean ignoreAspectRatio, boolean drawLetterboxHW, FrameBuffer buffer) {
    super(context);
    this.ignoreAspectRatio = ignoreAspectRatio;
    this.drawLetterboxHW = drawLetterboxHW;
    this.frameBuffer = (buffer != null) ? buffer : FrameBuffer.newNonBlockingFrameBuffer();
    if (ignoreAspectRatio) {
      this.blackBarLeftTop = null;
      this.blackBarRightBottom = null;
    } else {
      this.blackBarLeftTop = new Rect();
      this.blackBarRightBottom = new Rect();
    }
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
        if (!Periscope.HWUI || drawLetterboxHW || ignoreAspectRatio) {
          canvas.drawColor(Color.BLACK);
        }
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
        if (Periscope.HWUI) {
          Graphics.scaleRectKeepRatio(bw, bh, vw, vh, dstRect);
          if (drawLetterboxHW) {
            canvas.drawColor(Color.BLACK);
          }
        } else {
          Graphics.scaleRectKeepRatio(
              bw, bh, vw, vh, dstRect, blackBarLeftTop, blackBarRightBottom
          );
          canvas.drawRect(blackBarLeftTop, paint);
          canvas.drawRect(blackBarRightBottom, paint);
        }
      }
      canvas.drawBitmap(bitmap, null, dstRect, paint);
    } finally {
      frameBuffer.unlock();
    }
  }
}