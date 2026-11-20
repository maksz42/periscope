package com.maksz42.periscope.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.maksz42.periscope.R;
import com.maksz42.periscope.helper.Settings;

import java.util.List;

/**
 * The camera list, drawn in a small panel at the right. It never takes focus: the key service
 * moves its highlight, so the show keeps its focus, and keeps playing, while the list is up.
 * Main thread only.
 */
@RequiresApi(22)
final class ChooserOverlay {
  private final Context context;
  private WindowManager windows;
  private LinearLayout panel;

  ChooserOverlay(Context context) {
    this.context = context;
  }

  boolean isOpen() {
    return panel != null;
  }

  /**
   * Shows a title and rows, with one highlighted (-1 for none); replaces any rows shown.
   * {@code moreAbove} and {@code moreBelow} mark a list that continues past the rows shown.
   */
  void show(String title, List<String> rows, int highlighted, boolean moreAbove,
            boolean moreBelow) {
    DisplayMetrics dm = context.getResources().getDisplayMetrics();
    if (panel == null) {
      windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      panel = new LinearLayout(context);
      panel.setOrientation(LinearLayout.VERTICAL);
      int pad = Math.round(dm.heightPixels * 0.015f);
      panel.setPadding(pad, pad, pad, pad);
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(0xE0202020);
      bg.setCornerRadius(pad);
      panel.setBackground(bg);
      WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
          Math.round(dm.widthPixels * 0.22f),
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT
      );
      // Same side as the camera, at the opposite edge, so the two never overlap.
      OverlayPlacement.Corner corner = Settings.getInstance().getOverlayCorner();
      lp.gravity = (corner.top ? Gravity.BOTTOM : Gravity.TOP) | (corner.right ? Gravity.RIGHT : Gravity.LEFT);
      lp.x = Math.round(dm.widthPixels * 0.03f);
      lp.y = Math.round(dm.heightPixels * 0.08f);
      lp.setTitle("PeriscopeCameraChooser");
      windows.addView(panel, lp);
    }
    panel.removeAllViews();
    TextView head = row(title, false);
    head.setTextColor(0xFFAAAAAA);
    head.setTextSize(13);
    panel.addView(head);
    if (moreAbove) panel.addView(more("▲"));
    for (int i = 0; i < rows.size(); i++) {
      panel.addView(row(rows.get(i), i == highlighted));
    }
    if (moreBelow) panel.addView(more("▼"));
    TextView hint = row(context.getString(R.string.overlay_chooser_hint), false);
    hint.setTextColor(0xFF888888);
    hint.setTextSize(11);
    panel.addView(hint);
  }

  private TextView more(String arrow) {
    TextView t = row(arrow, false);
    t.setTextColor(0xFF888888);
    t.setTextSize(11);
    t.setGravity(Gravity.CENTER_HORIZONTAL);
    return t;
  }

  /**
   * How many camera rows fit, at most {@code limit}: the panel may use about three quarters of
   * the screen height, after its title, hint and the two arrow rows.
   */
  int maxRows(int limit) {
    DisplayMetrics dm = context.getResources().getDisplayMetrics();
    TextView probe = row("Mg", false);
    probe.measure(0, 0);
    int rowHeight = Math.max(1, probe.getMeasuredHeight());
    int available = (int) (dm.heightPixels * 0.75f) - 4 * rowHeight;
    return Math.max(1, Math.min(limit, available / rowHeight));
  }

  private TextView row(String text, boolean highlighted) {
    TextView t = new TextView(context);
    t.setText(text);
    // One line per camera, so the row budget holds whatever the names are.
    t.setSingleLine(true);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    t.setTextSize(16);
    t.setTextColor(highlighted ? Color.BLACK : Color.WHITE);
    t.setPadding(16, 8, 16, 8);
    if (highlighted) {
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(Color.WHITE);
      bg.setCornerRadius(8);
      t.setBackground(bg);
    }
    return t;
  }

  void close() {
    if (panel != null) {
      windows.removeView(panel);
      panel = null;
    }
  }
}
