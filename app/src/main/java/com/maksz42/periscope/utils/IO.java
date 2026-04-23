package com.maksz42.periscope.utils;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
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

  public static void transferStream(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[8192];
    int bytesRead;
    while ((bytesRead = input.read(buffer)) != -1) {
      output.write(buffer, 0, bytesRead);
    }
  }

  // backport of java.io.InputStream#skipNBytes(long)
  public static void skipNBytes(InputStream input, long n) throws IOException {
    while (n > 0) {
      long ns = input.skip(n);
      if (ns > 0 && ns <= n) {
        // adjust number to skip
        n -= ns;
      } else if (ns == 0) { // no bytes skipped
        // read one byte to check for EOS
        if (input.read() == -1) {
          throw new EOFException();
        }
        // one byte read so decrement number to skip
        n--;
      } else { // skipped negative or too many bytes
        throw new IOException("Unable to skip exactly");
      }
    }
  }
}