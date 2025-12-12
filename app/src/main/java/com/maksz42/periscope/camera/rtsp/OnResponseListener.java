package com.maksz42.periscope.camera.rtsp;

import java.util.List;

interface OnResponseListener {
  void onResponse(int status, List<String> headers, byte[] body);
  void onError(Exception e);
}
