package com.maksz42.periscope.camera.rtsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.maksz42.periscope.BuildConfig;
import com.maksz42.periscope.utils.IO;
import com.maksz42.periscope.utils.UnsynchronizedBufferedInputStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;

public class RtspClient {
  private static final String TAG = "RtspClient";
  private static final byte[] userAgentHeader =
      ("User-Agent:Periscope/" + BuildConfig.VERSION_NAME + '\n').getBytes();
  private static final OnResponseListener NO_LISTENER = (headers, body) -> { };
  private static final Pair<String, OnResponseListener> RESTART = new Pair<>(null, null);

  private final String cameraName;
  private final byte[] basicAuthHeader;
  private final String host;
  private final int port;
  private final boolean overTls;
  private final LinkedBlockingQueue<Pair<String, OnResponseListener>> requestQueue = new LinkedBlockingQueue<>();
  private final ConcurrentLinkedQueue<OnResponseListener> responseQueue = new ConcurrentLinkedQueue<>();
  private final Thread IOWriterThread = new Thread(this::IOLoop);
  private Thread readerThread;
  private AudioPlayer audioPlayer;


  public RtspClient(String host, int port, String user, String password, String cameraName, boolean overTls) {
    this.host = host;
    this.port = port;
    this.overTls = overTls;
    this.cameraName = cameraName;
    if (user != null && !user.isBlank()) {
      byte[] key = "Authorization:Basic ".getBytes();
      byte[] auth = Base64.encode((user + ':' + password).getBytes(), Base64.NO_WRAP);
      this.basicAuthHeader = new byte[key.length + auth.length + 1];
      System.arraycopy(key, 0, this.basicAuthHeader, 0, key.length);
      System.arraycopy(auth, 0, this.basicAuthHeader, key.length, auth.length);
      this.basicAuthHeader[this.basicAuthHeader.length - 1] = '\n';
    } else {
      this.basicAuthHeader = null;
    }
  }

  private Socket connect() throws IOException {
    Socket sock = new Socket();
    SSLSocket sslSocket;
    try {
      sock.connect(new InetSocketAddress(host, port), 5000);
      sock.setSoTimeout(5000);
      if (!overTls) return sock;

      sslSocket = (SSLSocket) HttpsURLConnection.getDefaultSSLSocketFactory().createSocket(sock, host, port, true);
    } catch (IOException e) {
      sock.close();
      throw e;
    }

    try {
      sslSocket.startHandshake();
      HostnameVerifier hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
      if (!hostnameVerifier.verify(host, sslSocket.getSession())) throw new IOException("Hostname verification failed");
    } catch (IOException e) {
      sslSocket.close();
      throw e;
    }
    return sslSocket;
  }

  public void start() {
    IOWriterThread.start();
  }

  private OutputStream startPlaying() throws IOException {
    OnResponseListener setupListener = (headers, body) -> {
      String playReq = buildPlayRequest();
      sendRequestAsync(playReq, NO_LISTENER);
    };

    OnResponseListener describeListener = (headers, body) -> {
      String sdp = new String(body);
      int lineStart = sdp.indexOf("a=rtpmap:");
      int formatStart = sdp.indexOf(' ', lineStart) + 1;
      String format = sdp.substring(formatStart, sdp.indexOf('\r', formatStart));
      String[] formatParts = format.split("/");
      AudioEncoding encoding = AudioEncoding.valueOf(formatParts[0]);
      int rate = Integer.parseInt(formatParts[1]);


      int bufSize = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
      AudioTrack audioTrack = new AudioTrack(
          AudioManager.STREAM_MUSIC,
          rate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufSize,
          AudioTrack.MODE_STREAM);
      if (audioPlayer != null) {
        audioPlayer.shutdown();
      }
      audioPlayer = new AudioPlayer(encoding, audioTrack);

      String req = buildSetupRequest();
      sendRequestAsync(req, setupListener);
    };

    Socket sock = connect();
    InputStream in;
    OutputStream out;
    try {
      in = sock.getInputStream();
      out = sock.getOutputStream();
    } catch (IOException e) {
      Log.e(TAG, "Failed to connect", e);
        try {
          sock.close();
        } catch (IOException ignore) { }
      throw e;
    }

    readerThread = new Thread(() -> readLoop(new DataInputStream(new UnsynchronizedBufferedInputStream(in))));
    readerThread.start();

    sendRequestAsync(buildDescribeRequest(), describeListener);

    return new BufferedOutputStream(out);
  }

  public void stop() {
    // Even though audioPlayer isn't volatile and is owned by readerThread
    // there's no harm in trying to shutdown here. readerThread will ensure
    // audioPlayer is shutdown before exiting.
    AudioPlayer ap = audioPlayer;
    if (ap != null) {
      ap.shutdown();
    }

    IOWriterThread.interrupt();
  }

  private void readLoop(DataInputStream in) {
    try (in) {
      while (true) {
        byte first = in.readByte();
        if (first == '$') {
          readInterleaved(in);
        } else {
          readResponse(in, first);
        }
      }
    } catch (IOException | RuntimeException e) {
      Log.d(TAG, "readLoop", e);
      requestQueue.clear();
      requestQueue.offer(RESTART);
    } finally {
      if (audioPlayer != null) {
        audioPlayer.shutdown();
      }
    }
  }

  private void IOLoop() {
    try {
      while (true) {
        handleConnection();
        Thread.sleep(500);
      }
    } catch (InterruptedException ignore) { }
  }

  private void handleConnection() throws InterruptedException {
    OutputStream out;
    try {
      out = startPlaying();
    } catch (IOException e) {
      Log.d(TAG, "startPlaying", e);
      return;
    }

    try {
      writeLoop(out);
    } catch (IOException e) {
      Log.d(TAG, "writeLoop", e);
    }

    readerThread.join();
    requestQueue.clear();
    responseQueue.clear();
  }

  private void writeLoop(OutputStream out) throws IOException, InterruptedException {
    try (out) {
      while (true) {
        String payload;
        OnResponseListener listener;
        Pair<String, OnResponseListener> req = requestQueue.poll(50, TimeUnit.SECONDS);
        if (req == RESTART) {
          return;
        } else if (req == null) {
          payload = buildOptionsRequest();
          listener = NO_LISTENER;
        } else {
          payload = req.first;
          listener = req.second;
        }

        responseQueue.add(listener);
        out.write(payload.getBytes());
        out.write(userAgentHeader);
        if (basicAuthHeader != null) {
          out.write(basicAuthHeader);
        }
        out.write('\n');
        out.flush();
      }
    }
  }

  private void readInterleaved(DataInputStream in) throws IOException {
    int channel = in.readUnsignedByte();
    int len = in.readUnsignedShort();
    handleInterleaved(in, channel, len);
  }

  private void handleInterleaved(DataInputStream in, int channel, int len) throws IOException {
    if (channel == 0) {
      handleRtp(in, len);
    } else {
      IO.skipNBytes(in, len);
    }
  }

  private void readResponse(DataInputStream in, byte first) throws IOException {
    // who cares about invalid responses ¯\_(ツ)_/¯
    ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
    lineBuf.write(first);
    boolean lf = false;
    List<String> headers = new ArrayList<>();
    int contentLen = 0;
    while (true) {
      byte b = in.readByte();
      if (b == '\n') {
        if (lf) {
          break;
        }
        lf = true;
        String header = lineBuf.toString();
        lineBuf.reset();
        if (header.startsWith("Content-Length")) {
          contentLen = Integer.parseInt(header, header.indexOf(' ') + 1, header.length(), 10);
        } else {
          headers.add(header);
        }
      } else if (b != '\r') {
        lf = false;
        lineBuf.write(b);
      }
    }
    byte[] body = new byte[contentLen];
    in.readFully(body);
    String statusLine = headers.remove(0);
    int statusCodeStart = statusLine.indexOf(' ') + 1;
    int statusCodeEnd = statusCodeStart + 3;
    int status = Integer.parseInt(statusLine, statusCodeStart, statusCodeEnd, 10);
    if (status != 200) throw new IOException("Response status code: " + status);
    handleResponse(headers, body);
  }

  private void handleResponse(List<String> headers, byte[] body) {
    OnResponseListener listener = responseQueue.poll();
    listener.onResponse(headers, body);
  }

  private void handleRtp(DataInputStream in, int len) throws IOException {
    final int HEADER_SIZE = 12;
    IO.skipNBytes(in, HEADER_SIZE);
    audioPlayer.write(in, len - HEADER_SIZE);
  }

  private void sendRequestAsync(String payload, OnResponseListener listener) {
    requestQueue.offer(new Pair<>(payload, listener));
  }

  private String buildDescribeRequest() {
    return "DESCRIBE rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }

  private String buildOptionsRequest() {
    return "OPTIONS rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }

  private String buildSetupRequest() {
    return "SETUP rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma/trackID=0 RTSP/1.0\n"
        + "Transport:RTP/AVP/TCP;unicast;interleaved=0-1\n";
  }

  private String buildPlayRequest() {
    return "PLAY rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }
}
