package com.maksz42.periscope.helper;

/**
 * Rows and columns of an "equal tiles" grid: every camera gets the same size, and tiles left
 * over stay empty. Pure logic.
 */
public final class GridShape {
  public final int rows;
  public final int columns;

  private GridShape(int rows, int columns) {
    this.rows = rows;
    this.columns = columns;
  }

  /**
   * The near-square grid for {@code cameras}: the longer side of the screen gets
   * ceil(sqrt(cameras)) tiles, the other side as many as needed. 3 cameras on a landscape
   * screen make 2 x 2, 5 make 2 rows of 3, 7 make 3 x 3.
   */
  public static GridShape of(int cameras, boolean landscape) {
    if (cameras <= 0) return new GridShape(0, 0);
    int along = (int) Math.ceil(Math.sqrt(cameras));
    int across = (cameras + along - 1) / along;
    return landscape ? new GridShape(across, along) : new GridShape(along, across);
  }

  /** True when some tiles stay empty. */
  public boolean hasGaps(int cameras) {
    return rows * columns != cameras;
  }
}
