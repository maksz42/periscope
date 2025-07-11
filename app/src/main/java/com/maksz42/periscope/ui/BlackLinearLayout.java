package com.maksz42.periscope.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.widget.LinearLayout;

public class BlackLinearLayout extends LinearLayout {
  public BlackLinearLayout(Context context) {
    super(context);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawColor(Color.BLACK);
  }
}
