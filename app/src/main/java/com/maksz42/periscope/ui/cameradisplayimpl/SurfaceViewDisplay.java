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

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.buffering.SingleFrameBuffer;
import com.maksz42.periscope.ui.CameraDisplay;

public class SurfaceViewDisplay extends SurfaceView
    implements CameraDisplay, SurfaceHolder.Callback, FrameBuffer.OnFrameUpdateListener {

  private volatile int surfaceWidth;
  private volatile int surfaceHeight;
  private volatile boolean bitmapWaiting;
  private final Paint paint = new Paint();
  private final SingleFrameBuffer frameBuffer = new SingleFrameBuffer();
  private final Object newBitmapLock = new Object();
  private Thread drawingThread;


  public SurfaceViewDisplay(Context context) {
    super(context);
    getHolder().addCallback(this);
    frameBuffer.setOnFrameUpdateListener(this);
    paint.setFilterBitmap(true);
  }

  @Override
  public void onFrameUpdate() {
    drawBitmapRequest();
  }

  @Override
  public SingleFrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  @Override
  public void setIgnoreAspectRatio(boolean ignore) {
    // TODO
  }

  @Override
  public void surfaceCreated(@NonNull SurfaceHolder holder) {
    Rect frame = holder.getSurfaceFrame();
    surfaceWidth = frame.width();
    surfaceHeight = frame.height();
    drawingThread = newDrawingThread();
    drawingThread.start();
    drawBitmapRequest();
  }

  @Override
  public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    // TODO maybe double buffering of width and height needed
    surfaceWidth = width;
    surfaceHeight = height;
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
      while (!Thread.interrupted()) {
        synchronized (newBitmapLock) {
          while (!bitmapWaiting) {
            try {
              newBitmapLock.wait();
            } catch (InterruptedException e) {
              return;
            }
          }
          bitmapWaiting = false;
        }
        Rect dstRect = new Rect(0, 0, surfaceWidth, surfaceHeight);
        synchronized (frameBuffer) {
          Bitmap frame = frameBuffer.getFrame();
          if (frame == null) continue;
          SurfaceHolder holder = getHolder();
          if (Thread.interrupted()) return;
          Canvas canvas = holder.lockCanvas();
          if (canvas == null) continue;
          canvas.drawBitmap(frame, null, dstRect, paint);
          holder.unlockCanvasAndPost(canvas);
        }
      }
    });
  }
}
