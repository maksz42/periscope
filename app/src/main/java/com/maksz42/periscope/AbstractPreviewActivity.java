package com.maksz42.periscope;

import static android.view.View.FOCUS_LEFT;
import static android.view.View.GONE;
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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.frigate.InvalidCredentialsException;
import com.maksz42.periscope.helper.Settings;

import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;


public abstract class AbstractPreviewActivity extends Activity {
  private static final int UI_TIME = 5000;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable hideUIAction = () -> setUIVisible(false);
  private Dialog alertDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (Client.getBaseUrl() == null) {
      startActivity(new Intent(this, SettingsActivity.class));
    }
    super.setContentView(R.layout.activity_preview);
    // TODO figure this out
    // Without FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS android 14 emulator shows a black bar
    // in place of the navbar but only if SYSTEM_UI_FLAG_HIDE_NAVIGATION was set in
    // postDelayed() and the theme is @android:style/Theme(.*)
    // On my physical devices it doesn't matter
    getWindow().addFlags(FLAG_KEEP_SCREEN_ON | FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    addFloatingButton(android.R.drawable.ic_menu_preferences, SettingsActivity.class);

    // This is a hack for android tv for navigating in and out of the floating bar
    findViewById(R.id.floating_bar).requestFocus();
    findViewById(R.id.dummy_menu_focus_grabber).setOnFocusChangeListener((v, hasFocus) -> {
      if (hasFocus) {
        findViewById(R.id.floating_bar).requestFocus();
      }
    });
    View.OnFocusChangeListener fcl = (v, hasFocus) -> {
      if (!hasFocus) return;
      if (hasFocusableCameraView()) {
        getPreviewRoot().requestFocus();
      } else {
        v.focusSearch(FOCUS_LEFT).requestFocus();
      }
    };
    findViewById(R.id.dummy_preview_focus_grabber_1).setOnFocusChangeListener(fcl);
    findViewById(R.id.dummy_preview_focus_grabber_2).setOnFocusChangeListener(fcl);

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
    ViewGroup rootPreview = getPreviewRoot();
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
        ? rootPreview.hasExplicitFocusable()
        : rootPreview.hasFocusable();
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      int flags = 0;
      if (!visible) {
        flags = SYSTEM_UI_FLAG_FULLSCREEN
              | SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          // SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN can only be set on devices
          // that support immersive mode, so that the app is responsible
          // hiding/showing system bars
          flags |= SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                 | SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
      }
      getWindow().getDecorView().setSystemUiVisibility(flags);
    } else {
      getWindow().setFlags(visible ? 0 : FLAG_FULLSCREEN, FLAG_FULLSCREEN);
    }
  }

  protected void addFloatingButton(int drawableResID, Class<?> cls) {
    Resources res = getResources();
    int size = res.getDimensionPixelSize(R.dimen.floating_button_size);
    int padding = res.getDimensionPixelSize(R.dimen.floating_button_padding);
    int margin = res.getDimensionPixelSize(R.dimen.floating_button_margin);
    ImageButton btn = new ImageButton(this);
    btn.setPadding(padding, padding, padding, padding);
    btn.setImageResource(drawableResID);
    btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
    btn.setOnClickListener(v -> startActivity(new Intent(this, cls)));
    // TODO find a better way
    btn.setOnKeyListener((v, keyCode, event) -> {
      if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
        return true;
      }
      return keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !hasFocusableCameraView();
    });
    LinearLayout floatingBar = findViewById(R.id.floating_bar);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
    params.rightMargin = margin;
    floatingBar.addView(btn, floatingBar.getChildCount() - 2, params);
  }

  @Override
  public void setContentView(int layoutResID) {
    getLayoutInflater().inflate(layoutResID, resetPreviewRoot());
  }

  @Override
  public void setContentView(View view) {
    setContentView(view, null);
  }

  @Override
  public void setContentView(View view, ViewGroup.LayoutParams params) {
    ViewGroup previewRoot = resetPreviewRoot();
    if (view == null) {
      return;
    }
    if (params != null) {
      previewRoot.addView(view, params);
    } else {
      previewRoot.addView(view);
    }
  }

  private ViewGroup getPreviewRoot() {
    return findViewById(R.id.preview_root);
  }

  private ViewGroup resetPreviewRoot() {
    ViewGroup previewRoot = getPreviewRoot();
    previewRoot.removeAllViews();
    return previewRoot;
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

  protected void handleCommonErrors(Throwable e) {
    if (e instanceof InvalidCredentialsException) {
      Log.e(this.getClass().getName(), "Invalid credentials", e);
      showDialog(new AlertDialog.Builder(this)
          .setMessage(getString(R.string.invalid_credentials))
          .setPositiveButton(getString(R.string.change_credentials),
              (dialog, which) -> startActivity(new Intent(this, SettingsActivity.class))
          )
      );
    } else if (e.getCause() instanceof CertificateException) {
      Log.e(this.getClass().getName(), "Self-signed certificate error", e);
      showDialog(new AlertDialog.Builder(this)
          .setMessage(getString(R.string.self_signed_cert_info))
          .setPositiveButton(getString(R.string.disable_cert_verification),
              (dialog, which) -> startActivity(new Intent(this, SettingsActivity.class))
          )
      );
    } else if (e instanceof SSLException) {
      Log.e(this.getClass().getName(), "Device probably doesn't support TLS", e);
      showDialog(new AlertDialog.Builder(this)
          .setMessage(getString(R.string.tls_error))
          .setPositiveButton(getString(R.string.change_to_http),
              (dialog, which) -> startActivity(new Intent(this, SettingsActivity.class))
          )
      );
    } else {
      Log.d(this.getClass().getName(), e.toString());
    }
  }

  protected void showDialog(AlertDialog.Builder alertDialogBuilder) {
    runOnUiThread(() -> {
      if (alertDialog != null) return;
      Dialog dialog = alertDialogBuilder.create();
      dialog.setOnDismissListener(d -> alertDialog = null);
      alertDialog = dialog;
      dialog.show();
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (alertDialog != null) {
      alertDialog.dismiss();
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    scheduleUIHide();
    if (findViewById(R.id.floating_bar).getVisibility() != VISIBLE) {
      setUIVisible(true);
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  protected void checkForUpdates(int delay) {
    if (Settings.getInstance(this).getAutoCheckForUpdates()) {
      // TODO do this in AlarmManager or something
      handler.postDelayed(new Runnable() {
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
                      (dialog, which) -> um.downloadAndInstallUpdate()
                  )
                  .setNeutralButton(R.string.later, (dialog, which) ->
                      handler.postDelayed(this, 1000 * 60 * 60 * 24)
                  )
                  .setNegativeButton(R.string.ignore, (dialog, which) ->
                      Settings.getInstance(AbstractPreviewActivity.this).setAutoCheckForUpdates(false)
                  )
              )
          );
        }
      }, delay);
    }
  }
}