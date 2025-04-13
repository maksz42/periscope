package com.maksz42.periscope.ui.cameradisplayimpl;

import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static android.widget.ImageView.ScaleType.FIT_XY;


import android.content.Context;
import android.widget.ImageView;

import com.maksz42.periscope.buffering.DoubleFrameBuffer;
import com.maksz42.periscope.buffering.FrameBuffer;
import com.maksz42.periscope.buffering.SingleFrameBuffer;
import com.maksz42.periscope.ui.CameraDisplay;
import com.maksz42.periscope.utils.Misc;

public class ImageViewDisplay extends ImageView
    implements CameraDisplay, FrameBuffer.OnFrameUpdateListener {
  private final FrameBuffer frameBuffer = FrameBuffer.supportsInBitmap()
      ? new DoubleFrameBuffer()
      : new SingleFrameBuffer();


  public ImageViewDisplay(Context context) {
    super(context);
    frameBuffer.setOnFrameUpdateListener(this);
  }

  @Override
  public FrameBuffer getFrameBuffer() {
    return frameBuffer;
  }

  @Override
  public void setIgnoreAspectRatio(boolean ignore) {
    setScaleType(ignore ? FIT_XY : FIT_CENTER);
  }

  @Override
  public void onFrameUpdate() {
    Misc.runOnUIThread(() -> {
      synchronized (frameBuffer) {
        setImageBitmap(frameBuffer.getFrame());
      }
    });
  }
}