package com.maksz42.periscope.camera.rtsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import com.maksz42.periscope.BuildConfig;
import com.maksz42.periscope.utils.IO;

import java.io.BufferedInputStream;
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
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RtspClient {
  private static final String TAG = "RtspClient";
  private static final byte[] userAgentHeader =
      ("User-Agent:Periscope/" + BuildConfig.VERSION_NAME + '\n').getBytes();
  private static final OnResponseListener NO_LISTENER = new OnResponseListener() {
    @Override
    public void onResponse(int status, List<String> headers, byte[] body) { }

    @Override
    public void onError(Exception e) { }
  };

  private final byte[] basicAuthHeader;

  private Socket sock;
  private final String host;
  private final int port;

  private volatile OutputStream out;

  private Thread readerThread;
  private final ScheduledExecutorService ioExecutor = Executors.newSingleThreadScheduledExecutor();

  private final ConcurrentLinkedQueue<OnResponseListener> responseQueue = new ConcurrentLinkedQueue<>();
  private volatile AudioPlayer audioPlayer;

  private AudioEncoding encoding;
  private final String cameraName;
  private volatile String session;


  public RtspClient(String host, int port, String user, String password, String cameraName) {
    this.host = host;
    this.port = port;
    this.cameraName = cameraName;
    if (user != null && !user.isBlank()) {
      byte[] key = "Authorization:Basic ".getBytes();
      byte[] auth = Base64.encode((user + ':' + password).getBytes(), 0);
      this.basicAuthHeader = new byte[key.length + auth.length];
      System.arraycopy(key, 0, this.basicAuthHeader, 0, key.length);
      System.arraycopy(auth, 0, this.basicAuthHeader, key.length, auth.length);
    } else {
      this.basicAuthHeader = null;
    }
  }

  private void connect() throws IOException {
    sock = new Socket();
    sock.connect(new InetSocketAddress(host, port), 5000);
    sock.setSoTimeout(5000);
    out = new BufferedOutputStream(sock.getOutputStream());
    InputStream in = sock.getInputStream();
    readerThread = new Thread(() -> readLoop(new DataInputStream(new BufferedInputStream(in))));
    readerThread.start();
  }

  private void restart() {
    if (readerThread != null) {
      readerThread.interrupt();
    }
    try {
      ioExecutor.schedule(() -> {
        try {
          if (sock != null) {
            sock.close();
          }
          start();
        } catch (IOException e) {
          Log.e(TAG, "Error closing socket");
          restart();
        }
      }, 500, TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      logExecutionRejected(e);
    }
  }

  public void start() {
    OnResponseListener setupListener = new OnResponseListener() {
      @Override
      public void onResponse(int status, List<String> headers, byte[] body) {
        if (status != 200) {
          restart();
          return;
        }
        for (String header : headers) {
          String s = "Session: ";
          if (header.startsWith(s)) {
            session = header.substring(s.length(), header.indexOf(';'));
            String playReq = buildPlayRequest();
            sendRequestAsync(playReq, NO_LISTENER);

            final long delay = 45;
            try {
              ioExecutor.scheduleWithFixedDelay(() -> {
                String optionsReq = buildOptionsRequest();
                sendRequest(optionsReq, NO_LISTENER);
              }, delay, delay, TimeUnit.SECONDS);
            } catch (RejectedExecutionException e) {
              logExecutionRejected(e);
            }
            break;
          }
        }
      }

      @Override
      public void onError(Exception e) { }
    };

    OnResponseListener describeListener = new OnResponseListener() {
      @Override
      public void onResponse(int status, List<String> headers, byte[] body) {
        if (status != 200) {
          restart();
          return;
        }
        String sdp = new String(body);
        int lineStart = sdp.indexOf("a=rtpmap:");
        int formatStart = sdp.indexOf(' ', lineStart) + 1;
        String format = sdp.substring(formatStart, sdp.indexOf('\r', formatStart));
        String[] formatParts = format.split("/");
        encoding = AudioEncoding.valueOf(formatParts[0]);
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
      }

      @Override
      public void onError(Exception e) { }
    };

    try {
      ioExecutor.execute(() -> {
        try {
          connect();
          String req = buildDescribeRequest();
          sendRequest(req, describeListener);
        } catch (IOException e) {
          restart();
        }
      });
    } catch (RejectedExecutionException e) {
      logExecutionRejected(e);
    }
  }

  public void stop() {
    AudioPlayer ap = audioPlayer;
    if (ap != null) {
      ap.shutdown();
    }

    ioExecutor.execute(() -> {
      if (readerThread != null) {
        readerThread.interrupt();
      }
      try {
        if (sock != null) {
          sock.close();
        }
      } catch (IOException e) {
        Log.e(TAG, "RTSP stop error", e);
        throw new RuntimeException(e);
      } finally {
        ioExecutor.shutdownNow();
      }
    });
  }

  private void readLoop(DataInputStream in) {
    try {
      while (true) {
        byte first = in.readByte();
        if (first == '$') {
          readInterleaved(in);
        } else {
          readResponse(in, first);
        }
      }
    } catch (IOException e) {
      if (!Thread.interrupted()) {
        restart();
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
          contentLen = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
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
    handleResponse(status, headers, body);
  }

  private void handleResponse(int status, List<String> headers, byte[] body) {
    OnResponseListener listener = responseQueue.poll();
    listener.onResponse(status, headers, body);
  }

  private void handleRtp(DataInputStream in, int len) throws IOException {
    final int HEADER_SIZE = 12;
    IO.skipNBytes(in, HEADER_SIZE);
    audioPlayer.write(in, len - HEADER_SIZE);
  }

  private void sendRequestAsync(String request, OnResponseListener listener) {
    ioExecutor.execute(() -> sendRequest(request, listener));
  }

  private void sendRequest(String request, OnResponseListener listener) {
    try {
      responseQueue.add(listener);
      out.write(request.getBytes());
      out.write(userAgentHeader);
      if (basicAuthHeader != null) {
        out.write(basicAuthHeader);
      }
      out.write('\n');
      out.flush();
    } catch (IOException e) {
      responseQueue.remove(listener);
      listener.onError(e);
    }
  }

  private void logExecutionRejected(Throwable e) {
    Log.d(TAG, "Rejected execution", e);
  }

  private String buildDescribeRequest() {
    return "DESCRIBE rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }

  private String buildOptionsRequest() {
    return "OPTIONS rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n";
  }

  private String buildSetupRequest() {
    return "SETUP rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma/trackID=0 RTSP/1.0\n"
        + "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\n";
  }

  private String buildPlayRequest() {
    return "PLAY rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n"
        + "Session: " + session + '\n'
        + "Range: 0-\n";
  }

  private String buildTeardownRequest() {
    return "TEARDOWN rtsp://" + host + ':' + port + '/' + cameraName + "?audio=pcmu,pcma RTSP/1.0\n"
        + "Session: " + session + '\n';
  }
}
