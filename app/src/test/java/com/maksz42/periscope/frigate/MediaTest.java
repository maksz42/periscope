package com.maksz42.periscope.frigate;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MediaTest {
  @Test
  public void theEndpointIsUnchangedWithoutAHeight() {
    assertEquals("api/front/latest.jpg", new Media("front", Media.ImageFormat.JPG, -1).endpoint);
    assertEquals("api/front/latest.webp?quality=70", new Media("front", Media.ImageFormat.WEBP, 70).endpoint);
  }

  @Test
  public void aHeightUsesFrigatesHeightParameter() {
    assertEquals("api/front/latest.jpg?height=302", new Media("front", Media.ImageFormat.JPG, -1, 302).endpoint);
    assertEquals("api/front/latest.jpg?quality=70&height=302", new Media("front", Media.ImageFormat.JPG, 70, 302).endpoint);
  }
}
