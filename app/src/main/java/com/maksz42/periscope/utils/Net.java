package com.maksz42.periscope.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public final class Net {
  private Net() { }

  public static URLConnection openConnectionWithTimeout(URL url) throws IOException {
    return openConnectionWithTimeout(url, 5000, 5000);
  }

  public static URLConnection openConnectionWithTimeout(
      URL url, int connectTimeout, int readTimeout
  ) throws IOException {
    URLConnection conn = url.openConnection();
    conn.setConnectTimeout(connectTimeout);
    conn.setReadTimeout(readTimeout);
    return conn;
  }

  public static InputStream openStreamWithTimeout(URL url) throws IOException {
    return openStreamWithTimeout(url, 5000, 5000);
  }

  public static InputStream openStreamWithTimeout(
      URL url, int connectTimeout, int readTimeout
  ) throws IOException {
    return openConnectionWithTimeout(url, connectTimeout, readTimeout).getInputStream();
  }
}
