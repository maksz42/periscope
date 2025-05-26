package com.maksz42.periscope.utils;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import com.maksz42.periscope.R;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class Net {
  private static boolean tls13AlreadyEnabled = false;
  private static HostnameVerifier defaultHostnameVerifier;


  private Net() { }


  private static class CompositeTrustManager implements X509TrustManager {
    private final X509TrustManager defaultTrustManager;
    private final X509TrustManager customTrustManager;

    private CompositeTrustManager(KeyStore customKeyStore) throws GeneralSecurityException {
      String defaultAlgorithm = TrustManagerFactory.getDefaultAlgorithm();

      TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(defaultAlgorithm);
      defaultTmf.init((KeyStore) null);
      defaultTrustManager = (X509TrustManager) defaultTmf.getTrustManagers()[0];

      TrustManagerFactory customTmf = TrustManagerFactory.getInstance(defaultAlgorithm);
      customTmf.init(customKeyStore);
      customTrustManager = (X509TrustManager) customTmf.getTrustManagers()[0];
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      try {
        customTrustManager.checkServerTrusted(chain, authType);
      } catch (CertificateException e) {
        defaultTrustManager.checkServerTrusted(chain, authType);
      }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
      defaultTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return defaultTrustManager.getAcceptedIssuers();
    }
  }

  private static void loadCertificate(
      InputStream input, String alias, CertificateFactory cf, KeyStore keyStore) throws GeneralSecurityException
  {
    Certificate ca = cf.generateCertificate(input);
    keyStore.setCertificateEntry(alias, ca);
  }


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

  public static void configureSSLSocketFactory(Context context, boolean disableCertVerification) {
    if (canEnableTls13()) {
      Conscrypt.ProviderBuilder providerBuilder = Conscrypt.newProviderBuilder();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // https://github.com/google/conscrypt/issues/603
        providerBuilder.provideTrustManager(true);
      }
      Security.insertProviderAt(providerBuilder.build(), 1);
      tls13AlreadyEnabled = true;
    }

    TrustManager[] trustManagers;
    try {
      if (disableCertVerification) {
        trustManagers = new TrustManager[] {
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
        // Android 14 already has isrg_root_x1 and isrg_root_x2
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
          KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
          keyStore.load(null, null);
          CertificateFactory cf = CertificateFactory.getInstance("X.509");
          Resources res = context.getResources();
          // Android 7.1 already has isrg_root_x1
          if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            try (InputStream caInput = res.openRawResource(R.raw.isrgrootx1)) {
              loadCertificate(caInput, "isrg_root_x1", cf, keyStore);
            }
          }
          try (InputStream caInput = res.openRawResource(R.raw.isrgrootx2)) {
            loadCertificate(caInput, "isrg_root_x2", cf, keyStore);
          }
          trustManagers = new TrustManager[] { new CompositeTrustManager(keyStore) };
        } else {
          trustManagers = null;
        }

        if (defaultHostnameVerifier != null) {
          HttpsURLConnection.setDefaultHostnameVerifier(defaultHostnameVerifier);
        }
      }
      SSLContext sc = SSLContext.getInstance("TLS");
      sc.init(null, trustManagers, null);
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    } catch (GeneralSecurityException e) {
      Log.w("TLS", "Failed to configure SSLSocketFactory", e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
