package com.maksz42.periscope.utils;

import android.graphics.Rect;

public final class Graphics {
  private Graphics() { }

  public static void scaleRectKeepRatio(int srcW, int srcH, int limitW, int limitH, Rect dstRect) {
    int dstW;
    int dstH;
    if (srcW * limitH > srcH * limitW) {
      // width limit
      dstW = limitW;
      dstH = (srcH * limitW) / srcW;
    } else {
      // height limit
      dstH = limitH;
      dstW = (srcW * limitH) / srcH;
    }
    int left = (limitW - dstW) >>> 1;
    int top = (limitH - dstH) >>> 1;
    dstRect.set(left, top, left + dstW, top + dstH);
  }
}
