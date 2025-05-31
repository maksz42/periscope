package com.maksz42.periscope.utils;

import android.graphics.Rect;

public final class Graphics {
  private Graphics() { }

  public static void scaleRectKeepRatio(int srcW, int srcH, int limitW, int limitH, Rect outDstRect) {
    scaleRectKeepRatio(srcW, srcH, limitW, limitH, outDstRect, null, null);
  }

  public static void scaleRectKeepRatio(
      int srcW, int srcH, int limitW, int limitH,
      Rect outDstRect, Rect outBarLeftTop, Rect outBarRightBottom
  ) {
    int dstW, dstH;
    int dstLeft, dstTop, dstRight, dstBottom;
    if (srcW * limitH > srcH * limitW) {
      // width limit
      dstH = (srcH * limitW) / srcW;
      dstLeft = 0;
      dstTop = (limitH - dstH) >>> 1;
      dstRight = limitW;
      dstBottom = dstTop + dstH;
      if (outBarLeftTop != null) {
        outBarLeftTop.set(0, 0, dstRight, dstTop);
      }
      if (outBarRightBottom != null) {
        outBarRightBottom.set(0, dstBottom, dstRight, limitH);
      }
    } else {
      // height limit
      dstW = (srcW * limitH) / srcH;
      dstLeft = (limitW - dstW) >>> 1;
      dstTop = 0;
      dstRight = dstLeft + dstW;
      dstBottom = limitH;
      if (outBarLeftTop != null) {
        outBarLeftTop.set(0, 0, dstLeft, dstBottom);
      }
      if (outBarRightBottom != null) {
        outBarRightBottom.set(dstRight, 0, limitW, dstBottom);
      }
    }
    outDstRect.set(dstLeft, dstTop, dstRight, dstBottom);
  }
}
