package com.maksz42.periscope.frigate;

import com.maksz42.periscope.utils.Net;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Semaphore;

// As HttpURLConnection is global making Client static
// is reasonable, right?
public final class Client {
  public static class CloseableConnection implements Closeable {
    private volatile HttpURLConnection connection;

    CloseableConnection(HttpURLConnection connection) throws InterruptedException {
      this.connection = Objects.requireNonNull(connection);
      Semaphore.acquire();
    }

    public HttpURLConnection getConnection() {
      return connection;
    }

    @Override
    public synchronized void close() {
      if (connection != null) {
        connection = null;
        Semaphore.release();
      }
    }
  }


  public enum Protocol {
    HTTP,
    HTTPS,
  }

  public interface OnInvalidCredentialsListener {
    void onInvalidCredentials();
  }


  // Limit concurrent connections
  // Looks like HttpUrlConnection has pool of 5 connections, at least
  // on android 13. More concurrent connections cause constant closing
  // previous and opening new tcp connections. Even with this limit,
  // connections are closed/opened occasionally.
  // TODO investigate this
  private static final int CONCURRENT_CONNECTIONS = 5;
  private static final Semaphore Semaphore = new Semaphore(CONCURRENT_CONNECTIONS, true);

  private volatile static URL baseUrl;
  private volatile static String user;
  private volatile static String password;
  private volatile static String token;
  private volatile static OnInvalidCredentialsListener onInvalidCredentialsListener;

  private Client() { }

  public static void setup(Protocol protocol, String host, int port) throws MalformedURLException {
    if (host == null) {
      throw new NullPointerException("Host is null");
    }
    baseUrl = new URL(protocol.toString().toLowerCase(), host, port, "/");
  }

  public static void setCredentials(String user, String password) {
    Client.user = user;
    Client.password = (password != null) ? password : "";
    Client.token = null;
  }

  public static void setOnInvalidCredentialsListener(OnInvalidCredentialsListener listener) {
    Client.onInvalidCredentialsListener = listener;
  }

  static CloseableConnection openConnection(String endpoint, boolean enableCompression)
      throws IOException, InterruptedException {
    URL url = new URL(baseUrl, endpoint);
    HttpURLConnection conn = (HttpURLConnection) Net.openConnectionWithTimeout(url);
    // https://github.com/blakeblackshear/frigate/pull/17400
    // although my frigate 0.15 instance doesn't compress anything
    // except the first response for some reason
    if (!enableCompression) {
      conn.setRequestProperty("accept-encoding", "identity");
    }
    if (token == null && user != null && !user.isBlank()) {
      login();
    }
    if (token != null) {
      conn.setRequestProperty("cookie", token);
    }
    CloseableConnection closeableConnection = new CloseableConnection(conn);
//    if (conn.getResponseCode() == 401) {
//
//    }
    return closeableConnection;
  }

  static InputStream openStream(String endpoint, boolean enableCompression)
      throws IOException, InterruptedException {
    CloseableConnection cc = openConnection(endpoint, enableCompression);
    try {
      return new FilterInputStream(cc.getConnection().getInputStream()) {
        @Override
        public void close() throws IOException {
          super.close();
          cc.close();
        }
      };
    } catch (IOException e) {
      cc.close();
      throw e;
    }
  }

  public static URL getBaseUrl() {
    return baseUrl;
  }

  static void login() throws IOException, InterruptedException {
    String tokenCopy = token;
    Semaphore.acquire(CONCURRENT_CONNECTIONS);
    try {
      if (!Objects.equals(token, tokenCopy)) return;
      JSONObject cred = new JSONObject();
      try {
        cred.put("user", user)
            .put("password", password);
      } catch (JSONException e) {
        throw new InvalidCredentialsException("Shouldn't happen, the log may contain sensitive data", e);
      }

      URL url = new URL(baseUrl, "api/login");
      HttpURLConnection conn =
          (HttpURLConnection) Net.openConnectionWithTimeout(url, 5000, 1000);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);
      try (OutputStream os = conn.getOutputStream()) {
        os.write(cred.toString().getBytes("utf8"));
      }
      int responseCode = conn.getResponseCode();
      if (responseCode == 401) {
//        onInvalidCredentialsListener.onInvalidCredentials();
        throw new InvalidCredentialsException("Response code 401");
      } else if (responseCode != 200) {
        throw new InvalidResponseException();
      }
      token = conn.getHeaderField("set-cookie").split(";", 2)[0];
    } finally {
      Semaphore.release(CONCURRENT_CONNECTIONS);
    }
  }
}
