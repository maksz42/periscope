package com.maksz42.periscope.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.util.Log;
import android.view.WindowManager;

final public class DialogUtils {
  private static final String TAG = "DialogUtils";

  private DialogUtils() { }

  public static boolean civilShow(Dialog dialog, Activity activity) {
    if (activity.isFinishing()) {
      Log.w(TAG, "Failed to show dialog: activity finished");
      return false;
    }
    try {
      dialog.show();
      return true;
    } catch (WindowManager.BadTokenException e) {
      Log.e(TAG, "Exception while showing dialog", e);
      return false;
    }
  }

  public static boolean civilShow(AlertDialog.Builder builder, Activity activity) {
    return civilShow(builder.create(), activity);
  }
}
