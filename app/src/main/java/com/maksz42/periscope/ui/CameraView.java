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
import com.maksz42.periscope.camera.CameraPlayer;
import com.maksz42.periscope.frigate.InvalidCredentialsException;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.ui.cameradisplayimpl.ImageViewDisplay;
import com.maksz42.periscope.ui.cameradisplayimpl.SurfaceViewDisplay;

import java.util.Objects;

import javax.net.ssl.SSLException;

public class CameraView extends FrameLayout {
  public interface OnErrorListener {
    void onError(Throwable t);
  }

  public enum DisplayImplementation {
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
  private short timeout = -1;
  private Media.ImageFormat imageFormat;

  public CameraView(Context context, Media media, DisplayImplementation displayImplementation) {
    super(context);

    this.media = media;

    cameraDisplay = switch (displayImplementation) {
      case IMAGEVIEW -> new ImageViewDisplay(context);
      case SURFACEVIEW -> new SurfaceViewDisplay(context);
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

  public CameraDisplay getCameraDisplay() {
    return cameraDisplay;
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
    setLoading(false);
  }

  public void start(long initialDelay, long delay) {
    Objects.requireNonNull(media, "Media not set");
    cameraPlayer = new CameraPlayer(cameraDisplay.getFrameBuffer(), media);
    cameraPlayer.setOnErrorListener(this::onError);
    cameraPlayer.setOnNewFrameListener(this::onNewFrame);
    if (timeout > 0) {
      cameraPlayer.setTimeout(timeout);
    }
    if (imageFormat != null) {
      cameraPlayer.setImageFormat(imageFormat);
    }
    cameraPlayer.start(initialDelay, delay);
  }

  public void stop() {
    cameraPlayer.stop();
    setLoading(false);
  }

  public void setTimeout(short timeout) {
    this.timeout = timeout;
    if (cameraPlayer != null) {
      cameraPlayer.setTimeout(timeout);
    }
  }
  public void setImageFormat(Media.ImageFormat imageFormat) {
    this.imageFormat = imageFormat;
    if (cameraPlayer != null) {
      cameraPlayer.setImageFormat(imageFormat);
    }
  }

  public void setLoading(boolean loading) {
    loader.setVisibility(loading ? VISIBLE : GONE);
  }

  public void setIgnoreAspectRatio(boolean ignore) {
    cameraDisplay.setIgnoreAspectRatio(ignore);
  }
}
