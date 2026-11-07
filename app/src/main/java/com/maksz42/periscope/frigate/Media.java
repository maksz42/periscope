package com.maksz42.periscope.frigate;

import java.io.IOException;
import java.io.InputStream;

public class Media {
  public enum ImageFormat {
    WEBP,
    JPG
  }

  public final String name;
  public final String endpoint;

  public Media(String name, ImageFormat format, int quality) {
    this(name, format, quality, -1);
  }

  /**
   * @param height if positive, Frigate scales the picture to this height (keeping its aspect
   *     ratio) before sending it
   */
  public Media(String name, ImageFormat format, int quality, int height) {
    this.name = name;
    StringBuilder sb = new StringBuilder()
        .append("api/")
        .append(name)
        .append("/latest.")
        .append(format.toString().toLowerCase());
    // Frigate defaults to 70
    char separator = '?';
    if (quality >= 0) {
      sb.append(separator).append("quality=").append(quality);
      separator = '&';
    }
    if (height > 0) {
      sb.append(separator).append("height=").append(height);
    }
    this.endpoint = sb.toString();
  }

  public String getName() {
    return name;
  }

  public InputStream getLatestFrameInputStream() throws IOException, InterruptedException {
    return Client.openStream(endpoint, false);
  }
}
