package com.maksz42.periscope.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.R;
import com.maksz42.periscope.frigate.Media;
import com.maksz42.periscope.helper.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * One silent camera picture in the top-right corner, drawn over whatever is on the TV. It is
 * refreshed through Periscope's own Frigate client at the user's interval, shows how old the
 * picture is, and turns red when the picture is stale. It never takes focus, so the remote keeps
 * controlling the show. Main thread only.
 */
@RequiresApi(22)
final class CameraOverlay {
  private static final String TAG = "CameraOverlay";
  /** Never poll faster than this, whatever the grid's interval is. */
  private static final long MIN_INTERVAL_MS = 500;

  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final FrameStatus.Generation generation = new FrameStatus.Generation();
  /**
   * The download in progress, so closing can abort it rather than wait for its timeout. Guarded
   * by {@link #lock} together with the generation, so a retired fetch can neither publish nor
   * clear the handle of the camera that replaced it.
   */
  private final Object lock = new Object();
  private InputStream inFlight;
  private int inFlightGeneration;
  private WindowManager windows;
  private FrameLayout window;
  private ImageView image;
  private TextView label;
  private FrameStatus status;
  private ScheduledExecutorService worker;
  private String camera;
  /** What the label shows now, so a tick that changes nothing doesn't redraw the window. */
  private String shownText;
  private int shownColor;
  /**
   * The picture on screen, and the one it replaced. A replaced picture is recycled only at the
   * next replacement, a whole interval later, so the renderer never draws a recycled bitmap.
   */
  private Bitmap shown;
  private Bitmap replaced;

  private final Runnable tickLabel = new Runnable() {
    @Override
    public void run() {
      if (window == null) return;
      long now = SystemClock.elapsedRealtime();
      String text = labelText(now);
      if (!text.equals(shownText)) {
        label.setText(text);
        shownText = text;
      }
      int color = status.warning(now) ? 0xC0B00000 : 0x80000000;
      if (color != shownColor) {
        label.setBackgroundColor(color);
        shownColor = color;
      }
      main.postDelayed(this, 250);
    }
  };

  CameraOverlay(Context context) {
    this.context = context;
  }

  /** The camera on screen, or null. */
  String camera() {
    return camera;
  }

  /** Shows the camera, replacing whatever was showing. False if the window could not be added. */
  boolean open(String name) {
    close();
    camera = name;
    status = new FrameStatus();
    windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Settings settings = Settings.getInstance();
    OverlayPlacement.Corner corner = settings.getOverlayCorner();
    DisplayMetrics dm = context.getResources().getDisplayMetrics();
    int[] size = settings.getOverlaySize().fit(dm.widthPixels, dm.heightPixels);
    int w = size[0];
    int h = size[1];
    window = new FrameLayout(context);
    window.setBackgroundColor(Color.BLACK);
    image = new ImageView(context);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    window.addView(image, new FrameLayout.LayoutParams(w, h));
    label = new TextView(context);
    label.setTextColor(Color.WHITE);
    label.setTextSize(12);
    label.setPadding(8, 4, 8, 4);
    window.addView(label, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM | Gravity.START
    ));
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
        w,
        h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    );
    lp.gravity = (corner.top ? Gravity.TOP : Gravity.BOTTOM) | (corner.right ? Gravity.RIGHT : Gravity.LEFT);
    lp.x = Math.round(dm.widthPixels * 0.03f);
    lp.y = Math.round(dm.heightPixels * 0.05f);
    lp.alpha = settings.getOverlayTransparency().alpha;
    lp.setTitle("PeriscopeCameraOverlay");
    try {
      windows.addView(window, lp);
    } catch (RuntimeException e) {
      Log.e(TAG, "Camera window not added", e);
      window = null;
      camera = null;
      return false;
    }
    shownText = null;
    shownColor = 0;
    main.post(tickLabel);

    Media media = new Media(name, settings.getImageFormat(), -1, h);
    long interval = Math.max(MIN_INTERVAL_MS, settings.getInterval());
    int gen;
    synchronized (lock) {
      gen = generation.next();
    }
    worker = Executors.newSingleThreadScheduledExecutor();
    worker.scheduleWithFixedDelay(() -> fetch(gen, media), 0, interval, TimeUnit.MILLISECONDS);
    return true;
  }

  private void fetch(int gen, Media media) {
    Bitmap bitmap = null;
    long checksum = 0;
    String error = null;
    try (InputStream is = media.getLatestFrameInputStream()) {
      synchronized (lock) {
        if (!generation.isCurrent(gen)) return; // closed while connecting
        inFlight = is;
        inFlightGeneration = gen;
      }
      byte[] bytes = readAll(is);
      CRC32 crc = new CRC32();
      crc.update(bytes, 0, bytes.length);
      checksum = crc.getValue();
      bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
      if (bitmap == null) error = context.getString(R.string.overlay_bad_image);
    } catch (InterruptedException e) {
      return; // closing
    } catch (Exception e) {
      synchronized (lock) {
        if (!generation.isCurrent(gen)) return; // aborted by close()
      }
      error = e.getClass().getSimpleName();
    } finally {
      synchronized (lock) {
        if (inFlightGeneration == gen) inFlight = null;
      }
    }
    Bitmap frame = bitmap;
    long sum = checksum;
    String err = error;
    main.post(() -> {
      boolean current;
      synchronized (lock) {
        current = generation.isCurrent(gen);
      }
      if (!current || window == null) {
        if (frame != null) frame.recycle(); // never shown
        return;
      }
      if (frame == null) {
        status.error(err);
      } else if (!status.frame(SystemClock.elapsedRealtime(), sum)) {
        frame.recycle(); // the same picture again; the one on screen stays
      } else {
        image.setImageBitmap(frame);
        if (replaced != null) replaced.recycle();
        replaced = shown;
        shown = frame;
      }
    });
  }

  private static byte[] readAll(InputStream is) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
    byte[] buf = new byte[16 * 1024];
    for (int n; (n = is.read(buf)) != -1; ) {
      out.write(buf, 0, n);
    }
    return out.toByteArray();
  }

  private String labelText(long now) {
    if (!status.hasFrame()) {
      return status.error() == null
          ? context.getString(R.string.overlay_connecting, camera)
          : context.getString(R.string.overlay_no_picture, camera, status.error());
    }
    long seconds = status.ageMs(now) / 1000;
    if (!status.stale(now)) return context.getString(R.string.overlay_live, camera);
    return context.getString(R.string.overlay_stale, camera, seconds);
  }

  /** Removes the window and stops fetching. Safe to call when already closed. */
  void close() {
    InputStream stream;
    synchronized (lock) {
      generation.invalidate();
      stream = inFlight;
      inFlight = null;
    }
    main.removeCallbacks(tickLabel);
    if (worker != null) {
      worker.shutdownNow();
      worker = null;
    }
    if (stream != null) {
      // Closing a network stream can block briefly; never on the main thread.
      new Thread(() -> {
        try {
          stream.close();
        } catch (IOException ignored) { }
      }, "overlay-abort").start();
    }
    if (window != null) {
      windows.removeView(window);
      window = null;
      image = null;
      label = null;
    }
    Bitmap a = shown;
    Bitmap b = replaced;
    shown = null;
    replaced = null;
    // After the window's own teardown, which removeView() queued ahead of this.
    main.post(() -> {
      if (a != null) a.recycle();
      if (b != null) b.recycle();
    });
    camera = null;
  }
}
