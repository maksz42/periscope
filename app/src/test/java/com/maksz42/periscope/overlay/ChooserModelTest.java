package com.maksz42.periscope.overlay;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ChooserModelTest {
  private static final String CLOSE = "Close camera";

  @Test
  public void withNothingShowingItListsTheCamerasAndStartsAtTheFirst() {
    ChooserModel m = new ChooserModel(Arrays.asList("front", "garage"), null, CLOSE);
    assertEquals(Arrays.asList("front", "garage"), m.rows());
    assertEquals("front", m.selectedCamera());
    m.move(-1);
    assertEquals("wraps to the last camera", "garage", m.selectedCamera());
    m.move(5);
    assertFalse("no close row when nothing is showing", m.closeSelected());
  }

  @Test
  public void whileWatchingItStartsOnCloseSoClosingTakesOneOk() {
    ChooserModel m = new ChooserModel(Arrays.asList("front", "garage", "yard"), "garage", CLOSE);
    assertEquals(Arrays.asList("front", "garage", "yard", CLOSE), m.rows());
    assertTrue(m.closeSelected());
    assertNull(m.selectedCamera());
  }

  @Test
  public void theListWrapsBothWays() {
    ChooserModel m = new ChooserModel(Arrays.asList("front", "garage", "yard"), "garage", CLOSE);
    m.move(1);
    assertEquals("down from close wraps to the first camera", "front", m.selectedCamera());
    m.move(-1);
    assertTrue("up from the first camera wraps to close", m.closeSelected());
    m.move(-1);
    assertEquals("yard", m.selectedCamera());
    m.move(-5);
    assertEquals("five more steps up from yard, around the four rows", "garage", m.selectedCamera());
  }

  @Test
  public void aCameraNamedLikeTheCloseRowIsStillACamera() {
    ChooserModel m = new ChooserModel(Arrays.asList(CLOSE), CLOSE, CLOSE);
    assertTrue("starts on the real close row", m.closeSelected());
    m.move(1);
    assertEquals(CLOSE, m.selectedCamera());
    assertFalse(m.closeSelected());
  }

  @Test
  public void aCameraNoLongerOfferedStillStartsOnClose() {
    ChooserModel m = new ChooserModel(Arrays.asList("front"), "gone", CLOSE);
    assertTrue(m.closeSelected());
  }

  @Test
  public void noCamerasMeansNothingToSelect() {
    ChooserModel m = new ChooserModel(Collections.emptyList(), null, CLOSE);
    m.move(1);
    assertNull(m.selectedCamera());
    assertFalse(m.closeSelected());
  }

  @Test
  public void aLongListShowsARunOfRowsThatAlwaysContainsTheHighlight() {
    java.util.List<String> cams = new java.util.ArrayList<>();
    for (int i = 0; i < 20; i++) cams.add("cam" + i);
    ChooserModel m = new ChooserModel(cams, "cam5", CLOSE);
    assertEquals(21, m.rows().size());
    assertArrayEquals("starting on close shows the end of the list", new int[] {13, 21}, m.window(8));
    for (int step = 0; step < 45; step++) {
      int[] w = m.window(8);
      assertEquals(8, w[1] - w[0]);
      assertTrue("highlight " + m.index() + " in " + w[0] + ".." + w[1], w[0] <= m.index() && m.index() < w[1]);
      assertTrue(w[0] >= 0 && w[1] <= m.rows().size());
      m.move(step < 25 ? 1 : -1);
    }
  }

  @Test
  public void aShortListShowsEverything() {
    ChooserModel m = new ChooserModel(Arrays.asList("a", "b"), null, CLOSE);
    assertArrayEquals(new int[] {0, 2}, m.window(8));
  }

  @Test
  public void anEmptySelectionWhileWatchingStillOffersClose() {
    ChooserModel m = new ChooserModel(Collections.emptyList(), "front", CLOSE);
    assertEquals(Arrays.asList(CLOSE), m.rows());
    assertTrue(m.closeSelected());
    assertFalse(m.hasCameras());
  }

  @Test
  public void theListsOwnSelectionWinsThenTheGridsThenEveryCamera() {
    List<String> list = Arrays.asList("front_left", "garage");
    List<String> grid = Arrays.asList("front_left", "master_bedroom");
    assertEquals(list, ChooserModel.cameraSource(list, grid));
    assertEquals(grid, ChooserModel.cameraSource(null, grid));
    assertNull(ChooserModel.cameraSource(null, null));
    assertEquals(Collections.emptyList(), ChooserModel.cameraSource(Collections.emptyList(), grid));
  }

  @Test
  public void whileWatchingFullScreenIsOneUpFromClose() {
    ChooserModel m = new ChooserModel(Arrays.asList("front", "garage"), "garage", CLOSE, "Full", "Open");
    assertEquals(Arrays.asList("front", "garage", "Full", CLOSE), m.rows());
    assertEquals(ChooserModel.Choice.CLOSE, m.choice());
    m.move(-1);
    assertEquals(ChooserModel.Choice.FULL_SCREEN, m.choice());
    assertNull(m.selectedCamera());
    m.move(-1);
    assertEquals(ChooserModel.Choice.CAMERA, m.choice());
    assertEquals("garage", m.selectedCamera());
  }

  @Test
  public void notWatchingOpenTheAppIsOneUpFromTheFirstCamera() {
    ChooserModel m = new ChooserModel(Arrays.asList("front", "garage"), null, CLOSE, "Full", "Open");
    assertEquals(Arrays.asList("front", "garage", "Open"), m.rows());
    assertEquals(ChooserModel.Choice.CAMERA, m.choice());
    m.move(-1);
    assertEquals(ChooserModel.Choice.OPEN_APP, m.choice());
    assertFalse(m.closeSelected());
  }

  @Test
  public void withNoCamerasTheAppCanStillBeOpened() {
    ChooserModel m = new ChooserModel(Collections.emptyList(), null, CLOSE, "Full", "Open");
    assertFalse(m.hasCameras());
    assertEquals(ChooserModel.Choice.OPEN_APP, m.choice());
  }
}
