package com.maksz42.periscope.ui.cameradisplayimpl;

import static android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Process;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.maksz42.periscope.buffering.SingleFrameBuffer;
import com.maksz42.periscope.ui.CameraDisplay;
import com.maksz42.periscope.utils.Graphics;

public class SurfaceViewDisplay extends SurfaceView
    implements CameraDisplay, SurfaceHolder.Callback {

  private volatile boolean bitmapWaiting;
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final SingleFrameBuffer frameBuffer = new SingleFrameBuffer();
  private final Rect cachedRect;
  private final Object newBitmapLock = new Object();
  private Thread drawingThread;


  public SurfaceViewDisplay(Context context, boolean ignoreAspectRatio) {
    super(context);
    this.cachedRect = ignoreAspectRatio ? null : new Rect();
    getHolder().addCallback(this);
    frameBuffer.setOnFrameUpdateListener(this::drawBitmapRequest);
  }

  @Override
  public SingleFrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  @Override
  public void surfaceCreated(@NonNull SurfaceHolder holder) {
    drawingThread = newDrawingThread();
    drawingThread.start();
  }

  @Override
  public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    drawBitmapRequest();
  }

  @Override
  public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
    // block until bitmap drawing finishes
    // Attempt to fix random
    // java.lang.IllegalStateException: Surface has already been released.
    drawingThread.interrupt();
    while (true) {
      try {
        drawingThread.join();
        break;
      } catch (InterruptedException ignored) { }
    }
  }

  private void drawBitmapRequest() {
    synchronized (newBitmapLock) {
      bitmapWaiting = true;
      newBitmapLock.notify();
    }
  }

  private Thread newDrawingThread() {
    return new Thread(() -> {
      Process.setThreadPriority(THREAD_PRIORITY_MORE_FAVORABLE);
      try {
        while (true) {
          synchronized (newBitmapLock) {
            while (!bitmapWaiting) {
              newBitmapLock.wait();
            }
            bitmapWaiting = false;
          }
          frameBuffer.lockInterruptibly();
          try {
            Bitmap frame = frameBuffer.getFrame();
            if (frame == null) continue;
            SurfaceHolder holder = getHolder();
            Canvas canvas = holder.lockCanvas();
            if (canvas == null) continue;
            Rect surfaceRect = holder.getSurfaceFrame();
            Rect dstRect = surfaceRect;
            if (cachedRect != null) {
              int fw = frame.getWidth();
              int fh = frame.getHeight();
              int sw = surfaceRect.width();
              int sh = surfaceRect.height();
              Graphics.scaleRectKeepRatio(fw, fh, sw, sh, cachedRect);
              dstRect = cachedRect;
            }
            canvas.drawBitmap(frame, null, dstRect, paint);
            holder.unlockCanvasAndPost(canvas);
          } finally {
            frameBuffer.unlock();
          }
        }
      } catch (InterruptedException ignored) { }
    });
  }
}
