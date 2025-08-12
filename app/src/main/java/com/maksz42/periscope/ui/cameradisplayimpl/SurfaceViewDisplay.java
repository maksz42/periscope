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
  private final boolean ignoreAspectRatio;
  private volatile Rect surfaceRect;
  private Thread drawingThread;


  public SurfaceViewDisplay(Context context, boolean ignoreAspectRatio, SingleFrameBuffer buffer) {
    super(context);
    this.ignoreAspectRatio = ignoreAspectRatio;
    getHolder().addCallback(this);
    if (buffer != null) {
      buffer.newConsumer();
      this.frameBuffer = buffer;
    } else {
      this.frameBuffer = new SingleFrameBuffer(true);
    }
  }

  @Override
  public SingleFrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  @Override
  public void surfaceCreated(@NonNull SurfaceHolder holder) {
    drawingThread = new Thread(
        ignoreAspectRatio
        ? newDrafterIgnoreRatio()
        : newDrafterKeepRatio()
    );
    drawingThread.start();
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
    drawingThread.interrupt();
    while (true) {
      try {
        drawingThread.join();
        break;
      } catch (InterruptedException ignored) { }
    }
  }

  @Override
  public void requestDraw() {
    // Drawing uses await/signal directly
    // TODO redesign this
  }

  private Runnable newDrafterIgnoreRatio() {
    return () -> {
      Process.setThreadPriority(THREAD_PRIORITY_MORE_FAVORABLE);
      SurfaceHolder holder = getHolder();
      try {
        while (true) {
          frameBuffer.lockInterruptibly();
          try {
            frameBuffer.awaitFrameReady();
            Bitmap frame = frameBuffer.getFrame();
            if (frame == null) continue;
            Canvas canvas = holder.lockCanvas();
            if (canvas == null) continue;
            canvas.drawBitmap(frame, null, surfaceRect, paint);
            holder.unlockCanvasAndPost(canvas);
          } finally {
            frameBuffer.unlock();
          }
        }
      } catch (InterruptedException ignored) { /* exit loop */ }
    };
  }

  private Runnable newDrafterKeepRatio() {
    return () -> {
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
            Canvas canvas = holder.lockCanvas(dirtyRect);
            if (canvas == null) continue;
            if (!dirtyRect.equals(currentDstRect)) {
              canvas.drawRect(blackBarLeftTop, paint);
              canvas.drawRect(blackBarRightBottom, paint);
            }
            canvas.drawBitmap(frame, null, currentDstRect, paint);
            holder.unlockCanvasAndPost(canvas);
          } finally {
            frameBuffer.unlock();
          }
          prevDstRect.set(currentDstRect);
        }
      } catch (InterruptedException ignored) { /* exit loop */ }
    };
  }
}
