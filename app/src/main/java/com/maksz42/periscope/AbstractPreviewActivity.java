package com.maksz42.periscope;

import static android.view.View.GONE;
import static android.view.View.NO_ID;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.frigate.InvalidCredentialsException;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.ui.CameraView;

import java.lang.ref.WeakReference;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;


public abstract class AbstractPreviewActivity extends Activity {
  private static final String TAG = "AbstractPreviewActivity";
  private static final int UI_TIME = 5000;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable hideUIAction = () -> setUIVisible(false);
  private Dialog alertDialog;

  final protected CameraView.OnErrorListener cameraViewErrorListener =
      (t, cv) -> {
        Runnable recoverable = handleCommonErrors(t);
        if (recoverable != null) {
          cv.setOnNewFrameListener(cv_ -> {
            recoverable.run();
            cv_.setOnErrorListener(null);
          });
        }
      };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (Client.getBaseUrl() == null) {
      startActivity(new Intent(this, SettingsActivity.class));
    }
    setContentView(R.layout.activity_preview);

    int windowFlags = FLAG_KEEP_SCREEN_ON;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // TODO figure this out
      // Without FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS android 14 emulator shows a black bar
      // in place of the navbar but only if SYSTEM_UI_FLAG_HIDE_NAVIGATION was set in
      // postDelayed() and the theme is @android:style/Theme(.*)
      // On my physical devices it doesn't matter
      windowFlags |= FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
    }
    getWindow().addFlags(windowFlags);
    addFloatingButton(android.R.drawable.ic_menu_preferences, R.id.preferences_button, SettingsActivity.class);

    findViewById(R.id.floating_bar).requestFocus();

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      return;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      // this is needed only on devices that
      // don't support immersive mode
      setupSystemUIListener();
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      return;
    }

    ViewGroup content = findViewById(android.R.id.content);
    LayoutTransition layoutTransition = new LayoutTransition();
    layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
    layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
    content.setLayoutTransition(layoutTransition);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      return;
    }
    WindowManager.LayoutParams lp = getWindow().getAttributes();
    lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    getWindow().setAttributes(lp);
  }

  private boolean hasFocusableCameraView() {
    View rootPreview = getPreview();
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
        ? rootPreview.hasExplicitFocusable()
        : rootPreview.hasFocusable();
  }

  @Override
  protected void onStart() {
    super.onStart();
    scheduleUIHide();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      Settings settings = Settings.getInstance(this);
      if (!settings.getHideStatusBar() || !settings.getHideNavBar()) {
        setupSystemUIListener();
      } else {
        removeSystemUIListener();
      }
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      scheduleUIHide();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    handler.removeCallbacksAndMessages(null);
    showWallpaperMsg("");
    if (alertDialog != null) {
      alertDialog.dismiss();
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    scheduleUIHide();
    if (findViewById(R.id.floating_bar).getVisibility() == VISIBLE) {
      return super.dispatchTouchEvent(ev);
    }
    setUIVisible(true);
    return true;
  }

  @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private void setupSystemUIListener() {
    getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
      if (visibility == 0) { // if visible
        setAppUIVisible(true);
        scheduleUIHide();
      } else {
        setAppUIVisible(false);
      }
    });
  }

  @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private void removeSystemUIListener() {
    getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(null);
  }

  private void scheduleUIHide() {
    handler.removeCallbacks(hideUIAction);
    handler.postDelayed(hideUIAction, UI_TIME);
  }

  private void setUIVisible(boolean visible) {
    setAppUIVisible(visible);
    setSystemUIVisible(visible);
  }

  private void setAppUIVisible(boolean visible) {
    ViewGroup floatingBar = findViewById(R.id.floating_bar);
    floatingBar.setVisibility(visible ? VISIBLE : GONE);
    floatingBar.requestFocus();
    onAppUIVisibilityChange(visible);
  }

  protected void onAppUIVisibilityChange(boolean visible) { }

  @SuppressLint("InlinedApi")
  private void setSystemUIVisible(boolean visible) {
    Settings settings = Settings.getInstance(this);
    boolean hideStatusBar = settings.getHideStatusBar();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      int flags = 0;
      if (!visible) {
        boolean hideNavBar = settings.getHideNavBar();
        if (hideStatusBar) {
          flags |= SYSTEM_UI_FLAG_FULLSCREEN;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN can only be set on devices
            // that support immersive mode, so that the app is responsible
            // hiding/showing system bars
            flags |= SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
          }
        }
        if (hideNavBar) {
          flags |= SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            && hideStatusBar
            && hideNavBar) {
          flags |= SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
      }
      getWindow().getDecorView().setSystemUiVisibility(flags);
    } else if (hideStatusBar) {
      getWindow().setFlags(visible ? 0 : FLAG_FULLSCREEN, FLAG_FULLSCREEN);
    }
  }

  protected ImageButton addFloatingButton(int drawableResID, Class<?> cls) {
    return addFloatingButton(drawableResID, NO_ID, cls);
  }

  protected ImageButton addFloatingButton(int drawableResID, int id, Class<?> cls) {
    return addFloatingButton(drawableResID, id, v -> startActivity(new Intent(this, cls)));
  }

  protected ImageButton addFloatingButton(int drawableResID, View.OnClickListener listener) {
    return addFloatingButton(drawableResID, NO_ID, listener);
  }

  protected ImageButton addFloatingButton(int drawableResID, int id, View.OnClickListener listener) {
    Resources res = getResources();
    int size = res.getDimensionPixelSize(R.dimen.floating_button_size);
    int padding = res.getDimensionPixelSize(R.dimen.floating_button_padding);
    int margin = res.getDimensionPixelSize(R.dimen.floating_button_margin);
    ImageButton btn = new ImageButton(this);
    btn.setPadding(padding, padding, padding, padding);
    btn.setImageResource(drawableResID);
    btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
    btn.setOnClickListener(listener);
    btn.setId(id);
    btn.setNextFocusDownId(R.id.focus_camera_view);
    btn.setNextFocusRightId(R.id.focus_camera_view);
    LinearLayout floatingBar = findViewById(R.id.floating_bar);
    int childCount = floatingBar.getChildCount();
    if (childCount > 0) {
      floatingBar.getChildAt(childCount - 1).setNextFocusRightId(NO_ID);
    }
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
    params.rightMargin = margin;
    floatingBar.addView(btn, params);
    return btn;
  }

  protected void removeFloatingButton(ImageButton button) {
    LinearLayout floatingBar = findViewById(R.id.floating_bar);
    floatingBar.removeView(button);
  }

  protected <T extends View> T getPreview() {
    return findViewById(R.id.preview_root);
  }

  protected void setPreview(View view) {
    View currentPreview = getPreview();
    ViewGroup parent = (ViewGroup) currentPreview.getParent();
    int index = parent.indexOfChild(currentPreview);
    parent.removeView(currentPreview);
    if (view == null) {
      view = new View(this);
      view.setVisibility(GONE);
    }
    view.setId(R.id.preview_root);
    parent.addView(view, index, currentPreview.getLayoutParams());
  }

  private TextView getWallpaperTextView() {
    return findViewById(R.id.wallpaper_msg);
  }

  protected void showWallpaperMsg(String msg) {
    TextView msgTextView = getWallpaperTextView();
    if (msg == null) {
      msgTextView.setVisibility(View.GONE);
    } else {
      msgTextView.setText(msg);
      msgTextView.setVisibility(View.VISIBLE);
    }
  }

  protected Runnable handleCommonErrors(Throwable e) {
    if (e instanceof InvalidCredentialsException) {
      Log.e(TAG, "Invalid credentials", e);
      return showDialog(new AlertDialog.Builder(this)
          .setMessage(getString(R.string.invalid_credentials))
          .setPositiveButton(getString(R.string.change_credentials),
              (dialog, which) -> startActivity(new Intent(this, SettingsActivity.class))
          )
      );
    } else if (e.getCause() instanceof CertificateException) {
      Log.e(TAG, "Self-signed certificate error", e);
      return showDialog(new AlertDialog.Builder(this)
          .setMessage(getString(R.string.self_signed_cert_info))
          .setPositiveButton(getString(R.string.disable_cert_verification),
              (dialog, which) -> startActivity(new Intent(this, SettingsActivity.class))
          )
      );
    } else if (e instanceof SSLException) {
      Log.e(TAG, "Device probably doesn't support TLS", e);
      return showDialog(new AlertDialog.Builder(this)
          .setMessage(getString(R.string.tls_error))
          .setPositiveButton(getString(R.string.change_to_http),
              (dialog, which) -> startActivity(new Intent(this, SettingsActivity.class))
          )
      );
    } else {
      Log.d(TAG, e.toString());
      return null;
    }
  }

  private Runnable showDialog(AlertDialog.Builder alertDialogBuilder) {
    return showDialog(alertDialogBuilder, false);
  }

  private Runnable showDialog(AlertDialog.Builder alertDialogBuilder, boolean forceShow) {
    if (alertDialog != null && !forceShow) {
      return null;
    }

    Dialog dialog = alertDialogBuilder.create();
    dialog.setOnDismissListener(d -> {
      if (d == alertDialog) {
        alertDialog = null;
      }
    });
    alertDialog = dialog;
    try {
      dialog.show();
    } catch (WindowManager.BadTokenException e) {
      Log.e(TAG, "Dialog show failed", e);
      return null;
    }

    return dialog::dismiss;
  }

  protected boolean shouldIgnoreFirstKey(int keyCode) {
    return true;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    scheduleUIHide();
    if (findViewById(R.id.floating_bar).getVisibility() != VISIBLE) {
      setUIVisible(true);
      if (shouldIgnoreFirstKey(event.getKeyCode())) {
        return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }

  private UpdateManager.UpdateProgressListener showUpdateProgressDialog() {
    ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    TextView textView = new TextView(this);
    LinearLayout linearLayout = new LinearLayout(this);
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    int padding = getResources().getDimensionPixelSize(R.dimen.update_progress_dialog_padding);
    linearLayout.setPadding(padding, 0, padding, 0);
    linearLayout.addView(progressBar);
    linearLayout.addView(textView);
    WeakReference<LinearLayout> layoutRef = new WeakReference<>(linearLayout);
    showDialog(new AlertDialog.Builder(this)
        .setTitle(R.string.downloading_update)
        .setView(linearLayout)
        .setCancelable(false)
        .setNegativeButton(R.string.hide, null),
        true
    );
    return new UpdateManager.UpdateProgressListener() {
      private void setProgressText(TextView textView, int val, int max) {
        textView.setText((val / 1024) + " / " + (max / 1024) + " KB");
      }

      @Override
      public void onSizeKnown(int size) {
        LinearLayout linearLayout = layoutRef.get();
        if (linearLayout == null) return;
        ProgressBar bar = (ProgressBar) linearLayout.getChildAt(0);
        TextView textView = (TextView) linearLayout.getChildAt(1);
        textView.setGravity(Gravity.RIGHT);
        if (size <= 0) {
          bar.setIndeterminate(true);
          textView.setText(null);
        } else {
          bar.setIndeterminate(false);
          bar.setMax(size);
          setProgressText(textView, bar.getProgress(), size);
        }
      }

      @Override
      public void onBytesDownloaded(int bytes) {
        LinearLayout linearLayout = layoutRef.get();
        if (linearLayout == null) return;
        ProgressBar bar = (ProgressBar) linearLayout.getChildAt(0);
        TextView textView = (TextView) linearLayout.getChildAt(1);
        bar.setProgress(bytes);
        setProgressText(textView, bytes, bar.getMax());
      }

      @Override
      public void onError(Throwable t) {
        LinearLayout linearLayout = layoutRef.get();
        if (linearLayout == null) return;
        TextView textView = (TextView) linearLayout.getChildAt(1);
        textView.append('\n' + t.toString());
      }
    };
  }

  protected void checkForUpdates(int delay) {
    // TODO do this in AlarmManager or something
    Settings settings = Settings.getInstance(this);
    if (!settings.getAutoCheckForUpdates()) return;
    Runnable updateCheckAction = new Runnable() {
      @Override
      public void run() {
        UpdateManager updateManager = new UpdateManager(AbstractPreviewActivity.this);
        updateManager.checkForUpdate((um, versionName, changelog) ->
            showDialog(new AlertDialog.Builder(AbstractPreviewActivity.this)
                .setTitle(getString(R.string.new_update, versionName))
                .setMessage(changelog)
                .setCancelable(false)
                .setPositiveButton(
                    R.string.install_update,
                    (dialog, which) -> um.downloadAndInstallUpdate(showUpdateProgressDialog())
                )
                .setNeutralButton(R.string.later, (dialog, which) -> {
                  final long dayMillis = 1000 * 60 * 60 * 24;
                  handler.postDelayed(this, dayMillis);
                  Settings.getInstance(AbstractPreviewActivity.this)
                      .setNextUpdateCheckTime(System.currentTimeMillis() + dayMillis)
                      .apply();
                })
                .setNegativeButton(R.string.ignore, (dialog, which) ->
                    Settings.getInstance(AbstractPreviewActivity.this)
                        .setAutoCheckForUpdates(false)
                        .apply()
                )
            )
        );
      }
    };
    long currentTime = System.currentTimeMillis();
    long requestedUpdateTime = currentTime + delay;
    long nextUpdateTime = settings.getNextUpdateCheckTime();
    handler.postDelayed(
        updateCheckAction,
        (nextUpdateTime > requestedUpdateTime) ? nextUpdateTime - currentTime : delay
    );
  }
}
