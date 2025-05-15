package com.maksz42.periscope.ui;

import com.maksz42.periscope.buffering.FrameBuffer;

public interface CameraDisplay {
  FrameBuffer getFrameBuffer();
  void requestDraw();
}
