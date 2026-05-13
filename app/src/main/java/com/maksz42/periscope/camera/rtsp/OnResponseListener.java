package com.maksz42.periscope.camera.rtsp;

import java.util.List;

interface OnResponseListener {
  void onResponse(List<String> headers, byte[] body);
}
