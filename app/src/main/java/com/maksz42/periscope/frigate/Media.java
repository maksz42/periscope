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

  public Media(String name, ImageFormat format) {
    this.name = name;
    this.endpoint = "api/" + name + "/latest." + format.toString().toLowerCase();
  }

  public String getName() {
    return name;
  }

  public InputStream getLatestFrameInputStream() throws IOException, InterruptedException {
    return Client.openStream(endpoint, false);
  }
}
