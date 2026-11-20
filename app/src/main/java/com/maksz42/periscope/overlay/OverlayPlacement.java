package com.maksz42.periscope.overlay;

/** Where the camera floats, how big it is and how see-through. Pure logic. */
public final class OverlayPlacement {
  private OverlayPlacement() { }

  public enum Corner {
    TOP_RIGHT(true, true),
    TOP_LEFT(true, false),
    BOTTOM_RIGHT(false, true),
    BOTTOM_LEFT(false, false);

    public final boolean top;
    public final boolean right;

    Corner(boolean top, boolean right) {
      this.top = top;
      this.right = right;
    }

    /** The stored value, or the top right corner for anything unknown. */
    public static Corner parse(String value) {
      for (Corner c : values()) {
        if (c.name().equals(value)) return c;
      }
      return TOP_RIGHT;
    }
  }

  public enum Size {
    SMALL(0.28f),
    MEDIUM(0.36f),
    LARGE(0.45f),
    HUGE(0.80f);

    /**
     * The camera's width as a share of the screen's longer side; {@link #fit} keeps it on
     * screen.
     */
    public final float share;

    Size(float share) {
      this.share = share;
    }

    /** The stored value, or large (the default) for anything unknown. */
    public static Size parse(String value) {
      for (Size s : values()) {
        if (s.name().equals(value)) return s;
      }
      return LARGE;
    }

    /**
     * The window's width and height (16:9) on a screen of this size: the share of the longer
     * side, shrunk if needed to leave a margin on a narrow or portrait screen.
     */
    public int[] fit(int screenWidth, int screenHeight) {
      int w = Math.round(Math.max(screenWidth, screenHeight) * share);
      w = Math.min(w, Math.round(screenWidth * 0.94f));
      w = Math.min(w, Math.round(screenHeight * 0.9f) * 16 / 9);
      return new int[] {w, w * 9 / 16};
    }
  }

  public enum Transparency {
    NONE(1f),
    LOW(0.85f),
    MEDIUM(0.7f),
    HIGH(0.5f);

    /** The window's opacity. */
    public final float alpha;

    Transparency(float alpha) {
      this.alpha = alpha;
    }

    /** The stored value, or low (the default) for anything unknown. */
    public static Transparency parse(String value) {
      for (Transparency t : values()) {
        if (t.name().equals(value)) return t;
      }
      return LOW;
    }
  }
}
