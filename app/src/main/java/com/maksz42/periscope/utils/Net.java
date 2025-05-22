package com.maksz42.periscope.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class Net {
  private static SSLSocketFactory defaultSSLSocketFactory;
  private static HostnameVerifier defaultHostnameVerifier;

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

  public static void disableCertVerification() {
    // https://stackoverflow.com/a/2893932
    if (defaultSSLSocketFactory == null) {
      defaultSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    }
    if (defaultHostnameVerifier == null) {
      defaultHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
    }
    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
          public void checkClientTrusted(X509Certificate[] certs, String authType) {
          }
          public void checkServerTrusted(X509Certificate[] certs, String authType) {
          }
        }
    };
    try {
      SSLContext sc = SSLContext.getInstance("TLS");
      sc.init(null, trustAllCerts, null);
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    } catch (GeneralSecurityException e) {
      e.printStackTrace();
    }
  }

  public static void enableCertVerification() {
    if (defaultSSLSocketFactory != null) {
      HttpsURLConnection.setDefaultSSLSocketFactory(defaultSSLSocketFactory);
    }
    if (defaultHostnameVerifier != null) {
      HttpsURLConnection.setDefaultHostnameVerifier(defaultHostnameVerifier);
    }
  }
}
