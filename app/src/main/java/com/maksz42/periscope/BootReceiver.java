package com.maksz42.periscope;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class BootReceiver extends BroadcastReceiver {
  public static void setEnabled(Context context, boolean enable) {
    ComponentName componentName = new ComponentName(context, BootReceiver.class);
    int newState = enable
        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    context.getPackageManager()
        .setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP
        );
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Intent i = new Intent(context, MatrixActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(i);
  }
}
