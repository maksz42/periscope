package com.maksz42.periscope.utils;

import android.os.Handler;
import android.os.Looper;

public final class Misc {
  private static final Thread UIThread = Looper.getMainLooper().getThread();
  private static final Handler handler = new Handler(Looper.getMainLooper());

  private Misc() { }

  public static boolean isUIThread() {
    return UIThread == Thread.currentThread();
  }

  public static void runOnUIThread(Runnable action) {
    if (isUIThread()) {
      action.run();
    } else {
      handler.post(action);
    }
  }

  public static boolean isNumeric(String s) {
    try {
      Integer.parseInt(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public static boolean inRange(int i, int min, int max) {
    return i >= min && i <= max;
  }

  public static boolean inRange(String s, int min, int max) {
    int i;
    try {
      i = Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return false;
    }
    return inRange(i, min, max);
  }
}
