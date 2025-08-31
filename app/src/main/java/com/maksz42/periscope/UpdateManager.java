package com.maksz42.periscope;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.utils.IO;
import com.maksz42.periscope.utils.Misc;
import com.maksz42.periscope.utils.Net;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

public class UpdateManager {
  public static class InstallReceiver extends BroadcastReceiver {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceive(Context context, Intent intent) {
      int status = intent.getIntExtra(
          PackageInstaller.EXTRA_STATUS,
          PackageInstaller.STATUS_FAILURE
      );
      if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
        Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      } else if (status >= PackageInstaller.STATUS_FAILURE) {
        Toast.makeText(
            context,
            "Error installing update. Status: " + status,
            Toast.LENGTH_LONG
        ).show();
      }
    }
  }

  private final static String TAG = "UpdateManager";
  private final static String APK_NAME = "periscope.apk.gz";
  private final static String UPDATE_URL = "http://periscope.freeddns.org/latest/";

  private final WeakReference<Context> ContextRef;


  public interface OnUpdateAvailableListener {
    void onUpdate(UpdateManager updateManager, String version, String changelog);
  }

  public interface UpdateProgressListener {
    void onSizeKnown(int size);
    void onBytesDownloaded(int bytes);
    void onError(Throwable t);
  }

  private static class UpdateDownloadObserver extends FilterInputStream implements Runnable {
    private final UpdateProgressListener updateProgressListener;
    private final Handler handler;
    private final AtomicBoolean posted = new AtomicBoolean();
    private volatile int bytes = 0;

    private UpdateDownloadObserver(InputStream in, Handler handler, UpdateProgressListener updateProgressListener) {
      super(in);
      this.updateProgressListener = updateProgressListener;
      this.handler = handler;
    }

    @Override
    public void run() {
      posted.set(false);
      updateProgressListener.onBytesDownloaded(bytes);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int readBytes = super.read(b, off, len);
      if (readBytes > 0) {
        bytes += readBytes;
        if (posted.compareAndSet(false, true)) {
          handler.post(this);
        }
      }
      return readBytes;
    }
  }

  public UpdateManager(Context context) {
    ContextRef = new WeakReference<>(context);
  }

  public void checkForUpdate(OnUpdateAvailableListener onUpdateAvailableListener) {
    new Thread(() -> {
      Context context = ContextRef.get();
      if (context != null) {
        new File(context.getFilesDir(), APK_NAME).delete();
      }
      try {
        URL url = new URL(UPDATE_URL + "version_code");
        String resp;
        try (InputStream is = Net.openStreamWithTimeout(url)) {
          resp = IO.readAllText(is);
        }
        int update_version_code = Integer.parseInt(resp.trim());
        if (update_version_code <= BuildConfig.VERSION_CODE) {
          return;
        }

        url = new URL(UPDATE_URL + "version_name");
        try (InputStream is = Net.openStreamWithTimeout(url)) {
          resp = IO.readAllText(is);
        }
        String versionName = resp;

        url = new URL(UPDATE_URL + "changelog");
        try (InputStream is = Net.openStreamWithTimeout(url)) {
          resp = IO.readAllText(is);
        }
        String changelog = resp;

        Misc.runOnUIThread(() -> onUpdateAvailableListener.onUpdate(this, versionName, changelog));
      } catch (IOException e) {
        Log.w(TAG, "Couldn't check for update", e);
      }
    }).start();
  }

  public void downloadAndInstallUpdate(UpdateProgressListener updateProgressListener) {
    new Thread(() -> {
      Context context = ContextRef.get();
      if (context == null) return;
      Handler handler = new Handler(Looper.getMainLooper());
      try {
        URL url = new URL(UPDATE_URL + APK_NAME);
        HttpURLConnection conn = (HttpURLConnection) Net.openConnectionWithTimeout(url);
        int len = conn.getContentLength();
        handler.post(() -> updateProgressListener.onSizeKnown(len));
        UpdateDownloadObserver updateDownloadObserver = new UpdateDownloadObserver(conn.getInputStream(), handler, updateProgressListener);
        try (InputStream ungzipInput = new GZIPInputStream(updateDownloadObserver, 32768)) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            installNewMethod(context, ungzipInput, len);
          } else {
            installOldMethod(context, ungzipInput);
          }
        }
        conn.disconnect();
      } catch (IOException e) {
        handler.post(() -> updateProgressListener.onError(e));
        Log.e(TAG, "Couldn't install update", e);
      }
    }).start();
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private void installNewMethod(Context context, InputStream input, int len) throws IOException {
    PackageInstaller installer = context.getPackageManager().getPackageInstaller();
    PackageInstaller.SessionParams params =
        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    int sessionId = installer.createSession(params);
    try (PackageInstaller.Session session = installer.openSession(sessionId)) {
      try (OutputStream output = session.openWrite(APK_NAME, 0, len)) {
        IO.transferStream(input, output);
        session.fsync(output);
      }
      PendingIntent callbackIntent = PendingIntent.getBroadcast(
          context,
          0,
          new Intent(context, InstallReceiver.class),
          PendingIntent.FLAG_MUTABLE
      );
      session.commit(callbackIntent.getIntentSender());
    }
  }

  private void installOldMethod(Context context, InputStream input) throws IOException {
    // see https://stackoverflow.com/a/47220833
    try (OutputStream output = context.openFileOutput(APK_NAME, Context.MODE_WORLD_READABLE)) {
      IO.transferStream(input, output);
    }
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(
        Uri.fromFile(new File(context.getFilesDir(), APK_NAME)),
        "application/vnd.android.package-archive"
    );
    context.startActivity(intent);
  }
}
