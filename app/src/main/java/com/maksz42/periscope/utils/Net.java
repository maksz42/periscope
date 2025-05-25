package com.maksz42.periscope.utils;

import android.os.Build;
import android.util.Log;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class Net {
  private static boolean tls13AlreadyEnabled = false;
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

  private static boolean canEnableTls13() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN
        || Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
      return false;
    }
    if (tls13AlreadyEnabled) {
      return false;
    }
    String abi = Misc.getPrimaryAbi();
    return abi.equals("armeabi-v7a")
        || abi.equals("arm64-v8a")
        || abi.equals("x86")
        || abi.equals("x86_64");
  }

  public static void configureSSLSocketFactory(boolean disableCertVerification) {
    if (canEnableTls13()) {
      Conscrypt.ProviderBuilder providerBuilder = Conscrypt.newProviderBuilder();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // https://github.com/google/conscrypt/issues/603
        providerBuilder.provideTrustManager(true);
      }
      Security.insertProviderAt(providerBuilder.build(), 1);
      tls13AlreadyEnabled = true;
    }

    TrustManager[] trustAllCerts = null;
    if (disableCertVerification) {
      trustAllCerts = new TrustManager[] {
          new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) { }
            public void checkServerTrusted(X509Certificate[] certs, String authType) { }
          }
      };
      if (defaultHostnameVerifier == null) {
        defaultHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
      }
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    } else {
      if (defaultHostnameVerifier != null) {
        HttpsURLConnection.setDefaultHostnameVerifier(defaultHostnameVerifier);
      }
    }
    try {
      SSLContext sc = SSLContext.getInstance("TLS");
      sc.init(null, trustAllCerts, null);
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    } catch (GeneralSecurityException e) {
      Log.w("TLS", "Failed to configure SSLSocketFactory", e);
    }
  }
}
