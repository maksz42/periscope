package com.maksz42.periscope.ui.cameradisplayimpl;

import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static android.widget.ImageView.ScaleType.FIT_XY;


import android.content.Context;
import android.widget.ImageView;

import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.buffering.LazyBitmapDrawable;
import com.maksz42.periscope.ui.CameraDisplay;

public class ImageViewDisplay extends ImageView implements CameraDisplay {
  public ImageViewDisplay(Context context) {
    super(context);
    LazyBitmapDrawable lazyBitmapDrawable = new LazyBitmapDrawable();
    lazyBitmapDrawable.getFrameBuffer().setOnFrameUpdateListener(this::postInvalidate);
    setImageDrawable(lazyBitmapDrawable);
  }

  @Override
  public FrameBuffer getFrameBuffer() {
    return ((LazyBitmapDrawable) getDrawable()).getFrameBuffer();
  }

  @Override
  public void setIgnoreAspectRatio(boolean ignore) {
    setScaleType(ignore ? FIT_XY : FIT_CENTER);
  }
}