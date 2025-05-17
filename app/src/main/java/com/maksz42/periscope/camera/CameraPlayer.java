package com.maksz42.periscope.camera;

import android.os.Handler;
import android.os.Looper;

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.utils.LoggingRunnable;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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


  private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final LoggingRunnable fetchImage;
  private final Runnable timeoutAction = () -> onError(new TimeoutException());
  private final FrameBuffer frameBuffer;
  private volatile Media media;
  private volatile OnNewFrameListener onNewFrameListener;
  private OnErrorListener onErrorListener;
  private Future<?> scheduledTask;

  {
    scheduledExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
  }

  public CameraPlayer(FrameBuffer frameBuffer, Media media, short timeout) {
    this.frameBuffer = frameBuffer;
    this.media = media;
    fetchImage = () -> {
      try {
        if (timeout > 0) {
          handler.postDelayed(timeoutAction, timeout);
        }
        try (InputStream input = this.media.getLatestFrameInputStream()) {
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

  public Media getMedia() {
    return media;
  }

  public void setMedia(Media media) {
    this.media = media;
  }

  public FrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  public void setOnNewFrameListener(OnNewFrameListener onNewFrameListener) {
    this.onNewFrameListener = onNewFrameListener;
  }

  public void setOnErrorListener(OnErrorListener onErrorListener) {
    this.onErrorListener = onErrorListener;
  }

  public void start(long initialDelay, long delay) {
    if (initialDelay != 0) {
      scheduledExecutor.execute(fetchImage);
    }
    scheduledTask = scheduledExecutor.scheduleWithFixedDelay(
        fetchImage,
        initialDelay,
        delay,
        TimeUnit.MILLISECONDS
    );
  }

  public void removeListeners() {
    onNewFrameListener = null;
    onErrorListener = null;
  }

  public void halt() {
    removeListeners();
    scheduledTask.cancel(false);
  }

  public void shutdown() {
    removeListeners();
    scheduledExecutor.shutdown();
  }

  public boolean isShutdown() {
    return scheduledExecutor.isShutdown();
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
