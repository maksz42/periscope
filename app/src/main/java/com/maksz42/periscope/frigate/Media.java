package com.maksz42.periscope.frigate;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.maksz42.periscope.utils.IO;

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

  public byte[] fetchLatestFrameAsBytes(ImageFormat format) throws IOException, InterruptedException {
    try (
        Client.CloseableConnection cc = Client.openConnection(getEndpoint(format), false);
        InputStream is = cc.get().getInputStream()
    ) {
      return IO.readAllBytes(is);
    }
  }

  /**
   * This is probably faster than fetchLatestFrameAsBytes()
   * on api < HONEYCOMB
   * @param format
   * @return
   */
  public Bitmap fetchLatestFrameAsBitmap(ImageFormat format) throws IOException, InterruptedException {
    try (
        Client.CloseableConnection cc = Client.openConnection(getEndpoint(format), false);
        InputStream is = cc.get().getInputStream()
    ) {
      return BitmapFactory.decodeStream(is);
    }
  }
}
