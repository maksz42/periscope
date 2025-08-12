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

  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final SingleFrameBuffer frameBuffer;
  private volatile Rect surfaceRect;
  private final Thread drawingThread;
  private volatile boolean paused;


  public SurfaceViewDisplay(Context context, boolean ignoreAspectRatio, SingleFrameBuffer buffer) {
    super(context);
    getHolder().addCallback(this);
    if (buffer != null) {
      buffer.newConsumer();
      this.frameBuffer = buffer;
    } else {
      this.frameBuffer = new SingleFrameBuffer(true);
    }
    this.drawingThread = ignoreAspectRatio ? newDrafterIgnoreRatio() : newDrafterKeepRatio();
    drawingThread.start();
  }

  @Override
  public SingleFrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  public void interruptDrawingThread() {
    paused = true;
    drawingThread.interrupt();
  }

  @Override
  public void surfaceCreated(@NonNull SurfaceHolder holder) {
    paused = false;
  }

  @Override
  public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    surfaceRect = new Rect(0, 0, width, height);
    frameBuffer.signalFrameReadyNonBlocking();
  }

  @Override
  public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
    // block until bitmap drawing finishes
    // Attempt to fix random
    // java.lang.IllegalStateException: Surface has already been released.
    synchronized (drawingThread) {
      paused = true;
    }
  }

  private Thread newDrafterIgnoreRatio() {
    return new Thread() {
      @Override
      public void run() {
        Process.setThreadPriority(THREAD_PRIORITY_MORE_FAVORABLE);
        SurfaceHolder holder = getHolder();
        try {
          while (true) {
            frameBuffer.lockInterruptibly();
            try {
              frameBuffer.awaitFrameReady();
              Bitmap frame = frameBuffer.getFrame();
              if (frame == null) continue;
              synchronized (this) {
                if (paused) continue;
                Canvas canvas = holder.lockCanvas();
                if (canvas == null) continue;
                canvas.drawBitmap(frame, null, surfaceRect, paint);
                holder.unlockCanvasAndPost(canvas);
              }
            } finally {
              frameBuffer.unlock();
            }
          }
        } catch (InterruptedException ignored) { /* exit loop */ }
      }
    };
  }

  private Thread newDrafterKeepRatio() {
    return new Thread() {
      @Override
      public void run() {
        Process.setThreadPriority(THREAD_PRIORITY_MORE_FAVORABLE);
        SurfaceHolder holder = getHolder();
        final Rect currentDstRect = new Rect();
        final Rect prevDstRect = new Rect();
        final Rect dirtyRect = new Rect();
        final Rect blackBarLeftTop = new Rect();
        final Rect blackBarRightBottom = new Rect();
        try {
          while (true) {
            frameBuffer.lockInterruptibly();
            try {
              frameBuffer.awaitFrameReady();
              Bitmap frame = frameBuffer.getFrame();
              if (frame == null) continue;
              Graphics.scaleRectKeepRatio(
                  frame.getWidth(), frame.getHeight(),
                  surfaceRect.right, surfaceRect.bottom,
                  currentDstRect, blackBarLeftTop, blackBarRightBottom
              );
              dirtyRect.set(currentDstRect);
              dirtyRect.union(prevDstRect);
              synchronized (this) {
                if (paused) return;
                Canvas canvas = holder.lockCanvas(dirtyRect);
                if (canvas == null) continue;
                if (!dirtyRect.equals(currentDstRect)) {
                  canvas.drawRect(blackBarLeftTop, paint);
                  canvas.drawRect(blackBarRightBottom, paint);
                }
                canvas.drawBitmap(frame, null, currentDstRect, paint);
                holder.unlockCanvasAndPost(canvas);
              }
            } finally {
              frameBuffer.unlock();
            }
            prevDstRect.set(currentDstRect);
          }
        } catch (InterruptedException ignored) { /* exit loop */ }
      }
    };
  }
}
