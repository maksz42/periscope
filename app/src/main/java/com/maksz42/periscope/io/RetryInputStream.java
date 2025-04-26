package com.maksz42.periscope.io;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RetryInputStream extends FilterInputStream {
  private final int MinRewind;
  private FastBAOS cache;
  private ByteArrayInputStream replay;

  /**
   * Creates a {@code RetryInputStream} object wrapped around
   * {@code in} input stream, allowing to reset the stream.
   * The cache is extended to the last {@code read()}, for example, if
   * {@code minRewind} is {@code 1024} and the current cache
   * size is {@code 1000}, if the next {@code read()} reads
   * {@code 100} bytes, the cache will be extended to {@code 1100} bytes.
   * The next {@code read()} will discard the cache, making it
   * impossible to {@code reset()}. So unless {@code minRewind} is
   * {@code 0}, the first {@code read()} is always possible to
   * {@code reset()}.
   * @param in underlying input stream
   * @param minRewind minimum guaranteed retry cache size
   */
  public RetryInputStream(InputStream in, int minRewind) {
    super(in);
    if (minRewind < 0) {
      throw new IllegalArgumentException("Negative initial size: " + minRewind);
    }
    this.MinRewind = minRewind;
    this.cache = new FastBAOS(minRewind);
  }

  @Override
  public int read() throws IOException {
    if (replay != null) {
      int b = replay.read();
      if (b != -1) {
        return b;
      }
      replay = null;
    }
    int b = super.read();
    if (cache != null && b != -1) {
      if (cache.size() < MinRewind) {
        cache.write(b);
      } else {
        cache = null;
      }
    }
    return b;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (replay != null) {
      int r = replay.read(b, off, len);
      if (r != -1) {
        return r;
      }
      replay = null;
    }
    int bytesRead = super.read(b, off, len);
    if (cache != null && bytesRead > 0) {
      if (cache.size() < MinRewind) {
        cache.write(b, off, bytesRead);
      } else {
        cache = null;
      }
    }
    return bytesRead;
  }

  @Override
  public void reset() throws IOException {
    if (cache == null) {
      throw new IOException("Retry buffer was discarded");
    }
    replay = new ByteArrayInputStream(cache.getBuffer(), 0, cache.size());
  }

  @Override
  public int available() throws IOException {
    int replyAvailable = (replay != null) ? replay.available() : 0;
    return replyAvailable + super.available();
  }

  @Override
  public long skip(long n) throws IOException {
    long skipped = 0;
    if (replay != null) {
      skipped = replay.skip(n);
      n -= skipped;
      if (n != 0) {
        replay = null;
      }
    }
    return skipped + super.skip(n);
  }

  @Override
  public void close() throws IOException {
    super.close();
    cache = null;
    replay = null;
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public void mark(int readlimit) { }
}
