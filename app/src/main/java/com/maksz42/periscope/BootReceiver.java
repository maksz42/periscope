package com.maksz42.periscope;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.maksz42.periscope.helper.Settings;

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

  public static boolean getEnabled(Context context) {
    ComponentName componentName = new ComponentName(context, BootReceiver.class);
    int state = context.getPackageManager().getComponentEnabledSetting(componentName);
    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!Settings.getInstance(context).getAutostart()) return;
    Intent i = new Intent(context, MatrixActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(i);
  }
}
