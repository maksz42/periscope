package com.maksz42.periscope.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GridShapeTest {
  private static void assertShape(int rows, int columns, GridShape shape) {
    assertEquals("rows", rows, shape.rows);
    assertEquals("columns", columns, shape.columns);
  }

  @Test
  public void landscapeGridsAreNearSquareAndWiderThanTall() {
    assertShape(1, 1, GridShape.of(1, true));
    assertShape(1, 2, GridShape.of(2, true));
    assertShape(2, 2, GridShape.of(3, true));
    assertShape(2, 2, GridShape.of(4, true));
    assertShape(2, 3, GridShape.of(5, true));
    assertShape(2, 3, GridShape.of(6, true));
    assertShape(3, 3, GridShape.of(7, true));
    assertShape(3, 3, GridShape.of(9, true));
    assertShape(3, 4, GridShape.of(10, true));
  }

  @Test
  public void portraitGridsAreTheSameTurnedSideways() {
    assertShape(2, 2, GridShape.of(3, false));
    assertShape(3, 2, GridShape.of(5, false));
  }

  @Test
  public void onlyUnevenCountsLeaveEmptyTiles() {
    assertTrue(GridShape.of(3, true).hasGaps(3));
    assertTrue(GridShape.of(5, true).hasGaps(5));
    assertFalse(GridShape.of(4, true).hasGaps(4));
    assertFalse(GridShape.of(6, true).hasGaps(6));
    assertFalse(GridShape.of(2, true).hasGaps(2));
  }

  @Test
  public void noCamerasNoGrid() {
    assertShape(0, 0, GridShape.of(0, true));
  }
}
