package com.maksz42.periscope.utils;

import android.graphics.Rect;

public final class Graphics {
  private Graphics() { }

  public static void scaleRectKeepRatio(int srcW, int srcH, int limitW, int limitH, Rect dstRect) {
    int dstW, dstH;
    int left, top, right, bottom;
    if (srcW * limitH > srcH * limitW) {
      // width limit
      dstH = (srcH * limitW) / srcW;
      left = 0;
      top = (limitH - dstH) >>> 1;
      right = limitW;
      bottom = top + dstH;
    } else {
      // height limit
      dstW = (srcW * limitH) / srcH;
      left = (limitW - dstW) >>> 1;
      top = 0;
      right = left + dstW;
      bottom = limitH;
    }
    dstRect.set(left, top, right, bottom);
  }
}
