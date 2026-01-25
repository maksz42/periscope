package com.maksz42.periscope.camera.rtsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import com.maksz42.periscope.BuildConfig;
import com.maksz42.periscope.media.audio.PCM;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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

  private volatile DataInputStream in;
  private volatile DataOutputStream out;

  private Thread readerThread;
  private final Thread mediaThread;
  private final ScheduledExecutorService ioExecutor = Executors.newSingleThreadScheduledExecutor();

  private final ConcurrentLinkedQueue<OnResponseListener> responseQueue = new ConcurrentLinkedQueue<>();
  private final LinkedBlockingQueue<short[]> mediaQueue = new LinkedBlockingQueue<>(8);

  private volatile AudioTrack audioTrack;
  private AudioEncoding encoding;
  private final String cameraName;
  private volatile String session;

  private volatile boolean mute = false;


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

    mediaThread = new Thread(() -> {
      try {
        while(true) {
          short[] data = mediaQueue.take();
          audioTrack.write(data, 0, data.length);
        }
      } catch (InterruptedException e) {
        Log.d(TAG, "Exit media thread");
      }
    });
    mediaThread.start();
  }

  private void connect() throws IOException {
    sock = new Socket();
    sock.connect(new InetSocketAddress(host, port), 5000);
    sock.setSoTimeout(5000);
    in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
    out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
    readerThread = new Thread(this::readLoop);
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
        if (audioTrack != null) {
          audioTrack.release();
        }
        audioTrack = new AudioTrack(
            AudioManager.STREAM_MUSIC,
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
            AudioTrack.MODE_STREAM);
        if (!mute) {
          mediaQueue.clear();
          audioTrack.play();
        }

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

  public boolean getMuted() {
    return mute;
  }

  public void setMuted(boolean mute) {
    this.mute = mute;
    if (audioTrack == null)  return;
    if (mute) {
      audioTrack.pause();
    } else {
      mediaQueue.clear();
      audioTrack.flush();
      audioTrack.play();
    }
  }

  public void stop() {
    mediaThread.interrupt();
    ioExecutor.execute(() -> {
      if (readerThread != null) {
        readerThread.interrupt();
      }
      try {
        if (sock != null) {
          sock.close();
        }
        if (audioTrack != null) {
          audioTrack.release();
        }
      } catch (IOException e) {
        Log.e(TAG, "RTSP stop error", e);
        throw new RuntimeException(e);
      } finally {
        ioExecutor.shutdownNow();
      }
    });
  }

  private void readLoop() {
    try {
      byte[] data = new byte[0xffff];
      while (true) {
        byte first = in.readByte();
        if (first == '$') {
          readInterleaved(data);
        } else {
          readResponse(data, first);
        }
      }
    } catch (IOException e) {
      if (!Thread.interrupted()) {
        restart();
      }
    }
  }

  private void readInterleaved(byte[] data) throws IOException {
    int channel = in.readUnsignedByte();
    int len = in.readUnsignedShort();
    in.readFully(data, 0, len);
    handleInterleaved(channel, data, len);
  }

  private void handleInterleaved(int channel, byte[] payload, int len) {
    if (channel == 0) {
      handleRtp(payload, len);
    }
  }

  private void readResponse(byte[] data, byte first) throws IOException {
    data[0] = first;
    boolean lf = false;
    int i = 1;
    List<String> headers = new ArrayList<>();
    int contentLen = 0;
    while (true) {
      byte b = in.readByte();
      if (b == '\n') {
        if (lf) {
          break;
        }
        lf = true;
        String header = new String(data, 0, i);
        i = 0;
        if (header.startsWith("Content-Length")) {
          contentLen = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
        } else {
          headers.add(header);
        }
      } else if (b != '\r') {
        lf = false;
        data[i++] = b;
      }
    }
    byte[] body = new byte[contentLen];
    in.readFully(body);
    String statusLine = headers.remove(0);
    int status = Integer.parseInt(statusLine.split(" ")[1]);
    handleResponse(status, headers, body);
  }

  private void handleResponse(int status, List<String> headers, byte[] body) {
    OnResponseListener listener = responseQueue.poll();
    listener.onResponse(status, headers, body);
  }

  private void handleRtp(byte[] payload, int len) {
//    ByteBuffer buffer = ByteBuffer.wrap(payload, 0, len);
//    int b0 = buffer.get() & 0xff;
//    boolean padding = ((b0 >> 5) & 1) == 1;
//    boolean extension = ((b0 >> 4) & 1) == 1;
//    int csrcCount = b0 & 0b1111;
//
//    int b1 = buffer.get() & 0xff;
//    boolean marker = (b1 >> 7) == 1;
//    int payloadType = b1 & 0b01111111;
//
//    int sequenceNumber = buffer.getShort() & 0xffff;
//
//    long timestamp = Integer.toUnsignedLong(buffer.getInt());
//
//    long ssrc = Integer.toUnsignedLong(buffer.getInt());
//
//    long[] csrc = new long[csrcCount];
//    for (int i = 0; i < csrcCount; i++) {
//      csrc[i] = Integer.toUnsignedLong(buffer.getInt());
//    }
//
//    if (extension) {
//      int extensionHeaderId = Short.toUnsignedInt(buffer.getShort());
//      int extensionHeaderLen = Short.toUnsignedInt(buffer.getShort());
//      long[] extensionnHeaderData = new long[extensionHeaderLen];
//      for (int i = 0; i < extensionHeaderLen; i++) {
//        extensionnHeaderData[i] = Integer.toUnsignedLong(buffer.getInt());
//      }
//    }
//
//    int rem = buffer.remaining();
//    short[] pcm = new short[rem];
//    for (int i = 0; i < rem; i++) {
//      pcm[i] = PCM.fromAlaw(buffer.get());
//    }

    final int HEADER_SIZE = 12;
    short[] pcm = new short[len - HEADER_SIZE];
    switch (encoding) {
      case PCMA -> {
        for (int i = 0; i < pcm.length; i++) {
          pcm[i] = PCM.fromALaw(payload[i + HEADER_SIZE]);
        }
      }
      case PCMU -> {
        for (int i = 0; i < pcm.length; i++) {
          pcm[i] = PCM.fromULaw(payload[i + HEADER_SIZE]);
        }
      }
    }
    mediaQueue.offer(pcm);
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
