package com.maksz42.periscope.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlayPlacementTest {
  @Test
  public void storedValuesParseAndUnknownOnesFallBack() {
    assertEquals(OverlayPlacement.Corner.BOTTOM_LEFT, OverlayPlacement.Corner.parse("BOTTOM_LEFT"));
    assertEquals(OverlayPlacement.Corner.TOP_RIGHT, OverlayPlacement.Corner.parse(null));
    assertEquals(OverlayPlacement.Corner.TOP_RIGHT, OverlayPlacement.Corner.parse("middle"));
    assertEquals(OverlayPlacement.Size.LARGE, OverlayPlacement.Size.parse("LARGE"));
    assertEquals(OverlayPlacement.Size.LARGE, OverlayPlacement.Size.parse(null));
    assertEquals(OverlayPlacement.Transparency.HIGH, OverlayPlacement.Transparency.parse("HIGH"));
    assertEquals(OverlayPlacement.Transparency.LOW, OverlayPlacement.Transparency.parse("clear"));
  }

  @Test
  public void cornersKnowTheirSides() {
    assertTrue(OverlayPlacement.Corner.TOP_LEFT.top);
    assertFalse(OverlayPlacement.Corner.TOP_LEFT.right);
    assertFalse(OverlayPlacement.Corner.BOTTOM_RIGHT.top);
    assertTrue(OverlayPlacement.Corner.BOTTOM_RIGHT.right);
  }

  @Test
  public void sizesGrow() {
    assertTrue(OverlayPlacement.Size.SMALL.share < OverlayPlacement.Size.MEDIUM.share);
    assertTrue(OverlayPlacement.Size.MEDIUM.share < OverlayPlacement.Size.LARGE.share);
    assertTrue(OverlayPlacement.Size.LARGE.share < OverlayPlacement.Size.HUGE.share);
  }

  @Test
  public void aWindowIsSixteenByNineAndStaysOnScreen() {
    assertArrayEquals(new int[] {538, 302}, OverlayPlacement.Size.SMALL.fit(1920, 1080));
    int[] huge = OverlayPlacement.Size.HUGE.fit(1920, 1080);
    assertEquals(1536, huge[0]);
    assertTrue(huge[1] <= 1080 * 0.9);
    int[] portrait = OverlayPlacement.Size.HUGE.fit(1080, 2400);
    assertTrue("narrower than a portrait phone", portrait[0] <= 1080 * 0.94 + 1);
  }

  @Test
  public void transparencyOnlyEverDims() {
    assertEquals(1f, OverlayPlacement.Transparency.NONE.alpha, 0f);
    for (OverlayPlacement.Transparency t : OverlayPlacement.Transparency.values()) {
      assertTrue(t.alpha > 0 && t.alpha <= 1);
    }
  }
}
