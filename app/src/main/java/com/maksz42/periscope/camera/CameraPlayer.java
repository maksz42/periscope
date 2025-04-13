package com.maksz42.periscope.camera;

import static android.os.Build.VERSION_CODES.HONEYCOMB;

import static com.maksz42.periscope.frigate.Media.ImageFormat.JPG;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.utils.LoggingRunnable;
import com.maksz42.periscope.utils.ThrowingAction;

import java.io.InterruptedIOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CameraPlayer {
  public interface OnNewFrameListener {
    /**
     * Guaranteed to run on the UI thread
     */
    void onNewFrame();
  }

  public interface OnErrorListener {
    /**
     * Guaranteed to run on the UI thread
     */
    void onError(Throwable throwable);
  }


  private Media.ImageFormat imageFormat = JPG;
  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable fetchAndDraw = createFetchAndDrawAction();
  private final Media media;
  private final FrameBuffer buffer;
  private final Runnable timeoutAction = () -> onError(new TimeoutException());
  private volatile short timeout = -1;
  private OnNewFrameListener onNewFrameListener;
  private OnErrorListener onErrorListener;


  public CameraPlayer(FrameBuffer buffer, Media media) {
    this.buffer = buffer;
    this.media = media;
  }

  public void setOnNewFrameListener(OnNewFrameListener onNewFrameListener) {
    this.onNewFrameListener = onNewFrameListener;
  }

  public void setOnErrorListener(OnErrorListener onErrorListener) {
    this.onErrorListener = onErrorListener;
  }

  public void setTimeout(short timeout) {
    this.timeout = timeout;
  }

  public void setImageFormat(Media.ImageFormat imageFormat) {
    this.imageFormat = imageFormat;
  }

  public void start(long initialDelay, long delay) {
    if (initialDelay != 0) {
      executorService.execute(fetchAndDraw);
    }
    executorService.scheduleWithFixedDelay(fetchAndDraw, initialDelay, delay, TimeUnit.MILLISECONDS);
  }

  public void stop() {
    onErrorListener = null;
    onNewFrameListener = null;
    executorService.shutdown();
  }

  private void onError(Throwable throwable) {
    handler.post(() -> {
      if (onErrorListener != null) {
        onErrorListener.onError(throwable);
      }
    });
  }

  private void onNewFrame() {
    handler.removeCallbacks(timeoutAction);
    handler.post(() -> {
      if (onNewFrameListener != null) {
        onNewFrameListener.onNewFrame();
      }
    });
  }

  private LoggingRunnable createFetchAndDrawAction() {
    ThrowingAction fetchAndDrawAction = FrameBuffer.supportsInBitmap()
        ? createFetchBytesAndDrawAction()
        : createFetchBitmapAndDrawAction();
    return () -> {
      try {
        if (timeout > 0) {
          handler.postDelayed(timeoutAction, timeout);
        }
        fetchAndDrawAction.run();
        onNewFrame();
      } catch (InterruptedIOException ignored) {
      } catch (Exception e) {
        onError(e);
        throw new RuntimeException(e);
      }
    };
  }

  @RequiresApi(HONEYCOMB)
  private ThrowingAction createFetchBytesAndDrawAction() {
    return () -> {
        byte[] frameData = media.fetchLatestFrameAsBytes(imageFormat);
        buffer.decodeByteArray(frameData);
    };
  }

  private ThrowingAction createFetchBitmapAndDrawAction() {
    return () -> {
      Bitmap bitmap = media.fetchLatestFrameAsBitmap(imageFormat);
      buffer.setFrame(bitmap);
    };
  }
}
