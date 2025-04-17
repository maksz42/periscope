package com.maksz42.periscope.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

  public static void saveToFile(InputStream input, File target) throws IOException {
    try (OutputStream output = new FileOutputStream(target)) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = input.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
      }
    }
  }

  public static void transferStream(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[8192];
    int bytesRead;
    while ((bytesRead = input.read(buffer)) != -1) {
      output.write(buffer, 0, bytesRead);
    }
  }
}