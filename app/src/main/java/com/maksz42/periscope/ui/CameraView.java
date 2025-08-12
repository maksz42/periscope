package com.maksz42.periscope.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.maksz42.periscope.R;
import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.buffering.SingleFrameBuffer;
import com.maksz42.periscope.camera.CameraPlayer;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.ui.cameradisplayimpl.BitmapDisplay;
import com.maksz42.periscope.ui.cameradisplayimpl.SurfaceViewDisplay;

public class CameraView extends FrameLayout {
  public interface OnErrorListener {
    void onError(Throwable t, CameraView cv);
  }

  public interface OnNewFrameListener {
    void onNewFrame(CameraView cv);
  }

  public enum DisplayImplementation {
    // it's no longer ImageView but changing would break existing configs
    IMAGEVIEW,
    SURFACEVIEW
  }

  private final ProgressBar loader;
  private final TextView cameraNameTextView;
  private final View focusView;
  private CameraPlayer cameraPlayer;
  private final CameraDisplay cameraDisplay;
  private final Media media;
  private OnErrorListener onErrorListener;
  private OnNewFrameListener onNewFrameListener;
  private final short timeout;



  public CameraView(
      Context context,
      Media media,
      DisplayImplementation displayImplementation,
      boolean ignoreAspectRatio,
      boolean drawLetterboxHW,
      short timeout,
      CameraPlayer cameraPlayer
      ) {
    super(context);
    this.media = media;
    this.timeout = timeout;
    this.cameraPlayer = cameraPlayer;

    FrameBuffer frameBuffer = (cameraPlayer != null)
        ? cameraPlayer.getFrameBuffer()
        : null;
    cameraDisplay = switch (displayImplementation) {
      case IMAGEVIEW -> new BitmapDisplay(context, ignoreAspectRatio, drawLetterboxHW, frameBuffer);
      case SURFACEVIEW -> new SurfaceViewDisplay(context, ignoreAspectRatio, (SingleFrameBuffer) frameBuffer);
    };
    addView((View) cameraDisplay, MATCH_PARENT, MATCH_PARENT);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setDefaultFocusHighlightEnabled(false);
    }
    focusView = new View(context);
    focusView.setBackgroundResource(R.drawable.border);
    focusView.setVisibility(GONE);
    addView(focusView, MATCH_PARENT, MATCH_PARENT);

    this.loader = new ProgressBar(context);
    if (frameBuffer != null && frameBuffer.getFrame() != null) {
      setLoading(false);
    }
    loader.setIndeterminate(true);
    addView(loader, MATCH_PARENT, MATCH_PARENT);

    cameraNameTextView = new TextView(context);
    cameraNameTextView.setText(media.getName());
    cameraNameTextView.setTextColor(Color.WHITE);
    cameraNameTextView.setBackgroundColor(Color.argb(192, 0, 0, 0));
    Resources res = getResources();
    int padding = res.getDimensionPixelSize(R.dimen.camera_name_padding);
    cameraNameTextView.setPadding(padding, 0, padding, 0);
    LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
    addView(cameraNameTextView, params);
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener l) {
    super.setOnClickListener(l);
    setFocusable(true);
  }

  @Override
  protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
    focusView.setVisibility(gainFocus ? VISIBLE : GONE);
    super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
  }

  public void setCameraNameVisible(boolean visible) {
    cameraNameTextView.setVisibility(visible ? VISIBLE : GONE);
  }

  public void setOnErrorListener(OnErrorListener onErrorListener) {
    this.onErrorListener = onErrorListener;
  }

  public void setOnNewFrameListener(OnNewFrameListener onNewFrameListener) {
    this.onNewFrameListener = onNewFrameListener;
  }

  private void onError(Throwable throwable) {
    setLoading(true);
    if (onErrorListener != null) {
      onErrorListener.onError(throwable, this);
    }
  }

  private void onNewFrame() {
    post(() -> {
      if (cameraDisplay instanceof BitmapDisplay bitmapDisplay) {
        bitmapDisplay.invalidate();
      }
      setLoading(false);
      if (onNewFrameListener != null) {
        onNewFrameListener.onNewFrame(this);
      }
    });
  }

  public void start(long initialDelay, long delay) {
    if (cameraPlayer != null) {
      cameraPlayer.setMedia(media);
    } else {
      cameraPlayer = new CameraPlayer(cameraDisplay.getFrameBuffer(), media, timeout);
    }
    cameraPlayer.setOnErrorListener(this::onError);
    cameraPlayer.setOnNewFrameListener(this::onNewFrame);
    cameraPlayer.start(initialDelay, delay);
  }

  public void stop() {
    if (cameraPlayer != null) {
      cameraPlayer.shutdown();
    }
    if (cameraDisplay instanceof SurfaceViewDisplay surfaceViewDisplay) {
      surfaceViewDisplay.interruptDrawingThread();
    }
    setLoading(false);
  }

  public void attachPlayer(CameraPlayer player) {
    cameraPlayer = player;
  }

  public CameraPlayer detachPlayer() {
    CameraPlayer player = cameraPlayer;
    if (player != null) {
      cameraPlayer = null;
      player.halt();
    }
    return player;
  }

  private void setLoading(boolean loading) {
    loader.setVisibility(loading ? VISIBLE : GONE);
  }

  public Media getMedia() {
    return media;
  }
}
