package com.maksz42.periscope.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class IO {
  private IO() { }

  public static byte[] readAllBytes(InputStream input) throws IOException
  {
    byte[] buffer = new byte[8192];
    int bytesRead;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    while ((bytesRead = input.read(buffer)) != -1) {
      output.write(buffer, 0, bytesRead);
    }
    return output.toByteArray();
  }

  public static String readAllText(InputStream input) throws IOException {
    return new String(readAllBytes(input), "utf8");
  }
}