package com.maksz42.periscope.camera.rtsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import com.maksz42.periscope.BuildConfig;
import com.maksz42.periscope.utils.IO;
import com.maksz42.periscope.utils.UnsynchronizedBufferedInputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;

public class RtspClient {
  private static enum State {
    BUFFERING,
    PLAYING,
  }

  private static final String TAG = "RtspClient";
  private static final byte[] userAgentHeader =
      ("User-Agent:Periscope/" + BuildConfig.VERSION_NAME + '\n').getBytes();

  private final byte[] describeRequest;
  private final byte[] optionsRequest;
  private final byte[] setupRequest;
  private final byte[] playRequest;
  private final String host;
  private final int port;
  private final boolean overTls;
  private final Thread IOThread = new Thread(this::IOLoop);
  private volatile Socket plainSock;
  private State state = State.BUFFERING;


  public RtspClient(String host, int port, String user, String password, String cameraName, boolean overTls) {
    this.host = host;
    this.port = port;
    this.overTls = overTls;

    byte[] basicAuthHeader = null;
    if (user != null && !user.isBlank()) {
      byte[] key = "Authorization:Basic ".getBytes();
      byte[] auth = Base64.encode((user + ':' + password).getBytes(), Base64.NO_WRAP);
      basicAuthHeader = new byte[key.length + auth.length + 1];
      System.arraycopy(key, 0, basicAuthHeader, 0, key.length);
      System.arraycopy(auth, 0, basicAuthHeader, key.length, auth.length);
      basicAuthHeader[basicAuthHeader.length - 1] = '\n';
    }

    this.describeRequest = buildRequestPayload(buildDescribeHead(cameraName), basicAuthHeader);
    this.optionsRequest = buildRequestPayload(buildOptionsHead(cameraName), basicAuthHeader);
    this.setupRequest = buildRequestPayload(buildSetupHead(cameraName), basicAuthHeader);
    this.playRequest = buildRequestPayload(buildPlayHead(cameraName), basicAuthHeader);
  }

  private Socket connect() throws IOException, InterruptedException {
    Socket sock = new Socket();
    plainSock = sock;
    if (Thread.interrupted()) {
      try {
        sock.close();
      } catch (IOException ignore) { }
      throw new InterruptedException();
    }

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
    IOThread.start();
  }

  public void stop() {
    IOThread.interrupt();
    Socket sock = plainSock;
    if (sock != null) {
      try {
        sock.close();
      } catch (IOException ignored) { }
    }
  }

  private byte[] sendRequestReadResponse(DataInputStream in, OutputStream out, byte[] request) throws IOException {
    out.write(request);
    return readResponse(in, (byte) 0);
  }

  private void sendRequestSkipResponse(DataInputStream in, OutputStream out, byte[] request) throws IOException {
    out.write(request);
    skipResponse(in, (byte) 0);
  }

  private void IOLoop() {
    AudioPlayer audioPlayer = null;
    try {
      while (true) {
        try (Socket sock = connect()) {
          DataInputStream in = new DataInputStream(new UnsynchronizedBufferedInputStream(sock.getInputStream()));
          OutputStream out = sock.getOutputStream();

          byte[] body = sendRequestReadResponse(in, out, describeRequest);
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

          sendRequestSkipResponse(in, out, setupRequest);
          long lastRequestTime = System.nanoTime();
          sendRequestSkipResponse(in, out, playRequest);

          while (true) {
            long now;
            do {
              handleInterleaved(in, audioPlayer);
              now = System.nanoTime();
            } while (now - lastRequestTime < 50_000_000_000L);

            lastRequestTime = now;
            out.write(optionsRequest);
            while (handleInterleaved(in, audioPlayer));
          }
        } catch (IOException | RuntimeException e) {
          if (Thread.interrupted()) return;
          Log.w(TAG, e);
        }
        Thread.sleep(500);
      }
    } catch (InterruptedException ignore) {
      // exit loop
    } finally {
      if (audioPlayer != null) {
        audioPlayer.shutdown();
      }
    }
  }

  private void consumeOrRebuffer(AudioPlayer audioPlayer) {
    if (audioPlayer.getCount() == 0) {
      state = State.BUFFERING;
    } else {
      audioPlayer.consume();
    }
  }

  private boolean handleInterleaved(DataInputStream in, AudioPlayer audioPlayer) throws IOException {
    final int HEADER_SIZE = 12;
    switch (state) {
      case BUFFERING -> {
        byte magic = in.readByte();
        if (magic != '$') {
          skipResponse(in, magic);
          return false;
        }

        int channel = in.readUnsignedByte();
        int rtpLen = in.readUnsignedShort();
        if (channel != 0) {
          IO.skipNBytes(in, rtpLen);
          return true;
        }

        IO.skipNBytes(in, HEADER_SIZE);

        int audioDataLen = rtpLen - HEADER_SIZE;

        audioPlayer.feed(in, audioDataLen);
        if (audioPlayer.getCount() * 2 > AudioPlayer.CAPACITY) {
          state = State.PLAYING;
          audioPlayer.consume();
        }
      }

      case PLAYING -> {
        int available = in.available();
        int box = 1 + 1 + 2 + HEADER_SIZE; // magic + channel + len + header

        if (box <= available) {
          byte magic = in.readByte();
          if (magic != '$') {
            consumeOrRebuffer(audioPlayer);
            skipResponse(in, magic);
            return false;
          }

          int channel = in.readUnsignedByte();
          int rtpLen = in.readUnsignedShort();
          available -= (1 + 1 + 2);
          if (channel != 0) {
            if (rtpLen > available) {
              consumeOrRebuffer(audioPlayer);
            }
            IO.skipNBytes(in, rtpLen);
            return true;
          }

          IO.skipNBytes(in, HEADER_SIZE);
          available -= HEADER_SIZE;
          int audioDataLen = rtpLen - HEADER_SIZE;

          if (audioDataLen <= available) {
            int count = audioPlayer.getCount();
            audioPlayer.feed(in, audioDataLen);
            if (count + audioDataLen > AudioPlayer.CAPACITY) {
              audioPlayer.halfFlush();
            }
          } else {
            consumeOrRebuffer(audioPlayer);
            audioPlayer.feed(in, audioDataLen);
          }
        } else {
          consumeOrRebuffer(audioPlayer);
        }
      }
    }
    return true;
  }

  private int parseResponse(DataInputStream in, byte first) throws IOException {
    // who cares about invalid responses ¯\_(ツ)_/¯
    ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
    if (first != 0) {
      lineBuf.write(first);
    }
    boolean lf = false;
    String statusLine = null;
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
        } else if (statusLine == null) {
          statusLine = header;
        }
      } else if (b != '\r') {
        lf = false;
        lineBuf.write(b);
      }
    }
    int statusCodeStart = statusLine.indexOf(' ') + 1;
    int statusCodeEnd = statusCodeStart + 3;
    int status = Integer.parseInt(statusLine, statusCodeStart, statusCodeEnd, 10);
    if (status != 200) throw new IOException("Response status code: " + status);
    return contentLen;
  }

  private byte[] readResponse(DataInputStream in, byte first) throws IOException {
    int contentLen = parseResponse(in, first);
    byte[] body = new byte[contentLen];
    in.readFully(body);
    return body;
  }

  private void skipResponse(DataInputStream in, byte first) throws IOException {
    int contentLen = parseResponse(in, first);
    IO.skipNBytes(in, contentLen);
  }

  private String buildDescribeHead(String cameraName) {
    return "DESCRIBE rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }

  private String buildOptionsHead(String cameraName) {
    return "OPTIONS rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }

  private String buildSetupHead(String cameraName) {
    return "SETUP rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma/trackID=0 RTSP/1.0\n"
        + "Transport:RTP/AVP/TCP;unicast;interleaved=0-1\n";
  }

  private String buildPlayHead(String cameraName) {
    return "PLAY rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }

  private byte[] buildRequestPayload(String head, byte[] basicAuthHeader) {
    int payloadLen = head.length() + userAgentHeader.length + 1;
    if (basicAuthHeader != null) {
      payloadLen += basicAuthHeader.length;
    }

    byte[] payload = new byte[payloadLen];
    head.getBytes(0, head.length(), payload, 0);
    int offset = head.length();

    System.arraycopy(userAgentHeader, 0, payload, offset, userAgentHeader.length);
    offset += userAgentHeader.length;

    if (basicAuthHeader != null) {
      System.arraycopy(basicAuthHeader, 0, payload, offset, basicAuthHeader.length);
      offset += basicAuthHeader.length;
    }

    payload[offset] = '\n';
    return payload;
  }
}
