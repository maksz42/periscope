package com.maksz42.periscope.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FrameStatusTest {
  @Test
  public void beforeAnyFrameItHasNoneAndCountsAsStale() {
    FrameStatus s = new FrameStatus();
    assertFalse(s.hasFrame());
    assertTrue(s.stale(0));
  }

  @Test
  public void aFreshFrameReportsItsAgeAndGoesStaleAfterThreeSeconds() {
    FrameStatus s = new FrameStatus();
    s.frame(10_000, 1);
    assertTrue(s.hasFrame());
    assertEquals(2_000, s.ageMs(12_000));
    assertFalse(s.stale(13_000));
    assertTrue("an old frame is never shown as live", s.stale(13_001));
  }

  @Test
  public void errorsAreKeptUntilTheNextFrame() {
    FrameStatus s = new FrameStatus();
    s.error("HTTP 500");
    assertEquals("HTTP 500", s.error());
    s.frame(1_000, 1);
    assertNull(s.error());
    s.error("SocketTimeoutException");
    assertTrue(s.stale(6_000));
    assertEquals("SocketTimeoutException", s.error());
  }

  @Test
  public void aResponseFromAnOlderSessionIsRejected() {
    FrameStatus.Generation g = new FrameStatus.Generation();
    int first = g.next();
    assertTrue(g.isCurrent(first));
    g.invalidate();
    assertFalse("closed window", g.isCurrent(first));
    int second = g.next();
    assertFalse("replaced camera", g.isCurrent(first));
    assertTrue(g.isCurrent(second));
  }

  @Test
  public void theSamePictureAgainDoesNotCountAsNew() {
    FrameStatus s = new FrameStatus();
    assertTrue(s.frame(0, 42));
    assertFalse("Frigate repeating a stopped camera's last picture", s.frame(2_000, 42));
    assertFalse(s.frame(4_000, 42));
    assertTrue("so it goes stale while downloads still succeed", s.stale(4_000));
    assertTrue(s.frame(5_000, 43));
    assertFalse(s.stale(5_000));
  }

  @Test
  public void aRepeatedPictureStillClearsTheLastError() {
    FrameStatus s = new FrameStatus();
    s.frame(0, 7);
    s.error("SocketTimeoutException");
    s.frame(1_000, 7);
    assertNull(s.error());
  }

  @Test
  public void waitingForTheFirstPictureIsNotAWarning() {
    FrameStatus s = new FrameStatus();
    assertFalse("connecting", s.warning(60_000));
    s.error("SocketTimeoutException");
    assertTrue("no picture could be fetched", s.warning(60_000));
  }

  @Test
  public void anOldPictureIsAWarning() {
    FrameStatus s = new FrameStatus();
    s.frame(10_000, 1);
    assertFalse(s.warning(10_000 + FrameStatus.STALE_MS));
    assertTrue(s.warning(10_001 + FrameStatus.STALE_MS));
  }
}
