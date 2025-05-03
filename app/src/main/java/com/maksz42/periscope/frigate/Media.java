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
    this.name = name;
    StringBuilder sb = new StringBuilder()
        .append("api/")
        .append(name)
        .append("/latest.")
        .append(format.toString().toLowerCase());
    // Frigate defaults to 70
    if (quality >= 0) {
      sb.append("?quality=").append(quality);
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
