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
import com.maksz42.periscope.camera.CameraPlayer;
import com.maksz42.periscope.frigate.InvalidCredentialsException;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.ui.cameradisplayimpl.BitmapDisplay;
import com.maksz42.periscope.ui.cameradisplayimpl.SurfaceViewDisplay;

import javax.net.ssl.SSLException;

public class CameraView extends FrameLayout {
  public interface OnErrorListener {
    void onError(Throwable t);
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
  private final short timeout;


  public CameraView(
      Context context,
      Media media,
      DisplayImplementation displayImplementation,
      boolean ignoreAspectRatio,
      short timeout
  ) {
    this(context, media, displayImplementation, ignoreAspectRatio, timeout, null);
  }

  public CameraView(
      Context context,
      Media media,
      DisplayImplementation displayImplementation,
      boolean ignoreAspectRatio,
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
      case IMAGEVIEW -> new BitmapDisplay(context, ignoreAspectRatio, frameBuffer);
      case SURFACEVIEW -> new SurfaceViewDisplay(context, ignoreAspectRatio, frameBuffer);
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

  private void onError(Throwable throwable) {
    if (throwable instanceof InvalidCredentialsException
        || throwable instanceof SSLException) {
      stop();
    }// else {
      setLoading(true);
    //}
    if (onErrorListener != null) {
      onErrorListener.onError(throwable);
    }
  }

  private void onNewFrame() {
    cameraDisplay.requestDraw();
    post(() -> setLoading(false));
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
    setLoading(false);
  }

  public CameraPlayer detachPlayer() {
    CameraPlayer player = cameraPlayer;
    cameraPlayer = null;
    player.halt();
    return player;
  }

  private void setLoading(boolean loading) {
    loader.setVisibility(loading ? VISIBLE : GONE);
  }
}
