package com.maksz42.periscope.frigate;

import java.io.IOException;
import java.io.InputStream;

public class Media {
  public enum ImageFormat {
    WEBP,
    JPG
  }

  public final String name;

  public Media(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  private String getEndpoint(ImageFormat format) {
    return "api/" + name + "/latest." + format.toString().toLowerCase();
  }

  public InputStream getLatestFrameInputStream(ImageFormat format) throws IOException, InterruptedException {
    return Client.openStream(getEndpoint(format), false);
  }
}
