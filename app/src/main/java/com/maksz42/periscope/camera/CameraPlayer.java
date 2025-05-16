package com.maksz42.periscope.camera;

import android.os.Handler;
import android.os.Looper;

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.utils.LoggingRunnable;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CameraPlayer {
  public interface OnNewFrameListener {
    void onNewFrame();
  }

  public interface OnErrorListener {
    /**
     * Guaranteed to run on the UI thread
     */
    void onError(Throwable throwable);
  }


  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final LoggingRunnable fetchImage;
  private final Runnable timeoutAction = () -> onError(new TimeoutException());
  private volatile short timeout = -1;
  private volatile OnNewFrameListener onNewFrameListener;
  private OnErrorListener onErrorListener;


  public CameraPlayer(FrameBuffer frameBuffer, Media media) {
    fetchImage = () -> {
      try {
        if (timeout > 0) {
          handler.postDelayed(timeoutAction, timeout);
        }
        try (InputStream input = media.getLatestFrameInputStream()) {
          frameBuffer.decodeStream(input);
        }
        onNewFrame();
      } catch (InterruptedIOException ignored) {
      } catch (Exception e) {
        handler.post(() -> onError(e));
        throw new RuntimeException(e);
      }
    };
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

  public void start(long initialDelay, long delay) {
    if (initialDelay != 0) {
      executorService.execute(fetchImage);
    }
    executorService.scheduleWithFixedDelay(fetchImage, initialDelay, delay, TimeUnit.MILLISECONDS);
  }

  public void stop() {
    onErrorListener = null;
    onNewFrameListener = null;
    executorService.shutdown();
  }

  /**
   * Make sure it's called on the UI thread
   */
  private void onError(Throwable throwable) {
    if (onErrorListener != null) {
      onErrorListener.onError(throwable);
    }
  }

  private void onNewFrame() {
    handler.removeCallbacks(timeoutAction);
    OnNewFrameListener listener = onNewFrameListener;
    if (listener != null) {
      listener.onNewFrame();
    }
  }
}
