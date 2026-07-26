/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maksz42.periscope.utils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps an existing {@link InputStream} and <em>buffers</em> the input.
 * Expensive interaction with the underlying input stream is minimized, since
 * most (smaller) requests can be satisfied by accessing the buffer alone. The
 * drawback is that some extra space is required to hold the buffer and that
 * copying takes place when filling that buffer, but this is usually outweighed
 * by the performance benefits.
 *
 * <p/>A typical application pattern for the class looks like this:<p/>
 *
 * <pre>
 * BufferedInputStream buf = new BufferedInputStream(new FileInputStream(&quot;file.java&quot;));
 * </pre>
 *
 * @see BufferedOutputStream
 */
public class UnsynchronizedBufferedInputStream extends InputStream {
  /**
   * The default buffer size if it is not specified.
   *
   * @hide
   */
  public static final int DEFAULT_BUFFER_SIZE = 8192;

  protected InputStream in;
  /**
   * The buffer containing the current bytes read from the target InputStream.
   */
  protected byte[] buf;

  /**
   * The total number of bytes inside the byte array {@code buf}.
   */
  protected int count;

  /**
   * The current limit, which when passed, invalidates the current mark.
   */
  protected int marklimit;

  /**
   * The currently marked position. -1 indicates no mark has been set or the
   * mark has been invalidated.
   */
  protected int markpos = -1;

  /**
   * The current position within the byte array {@code buf}.
   */
  protected int pos;

  /**
   * Constructs a new {@code BufferedInputStream}, providing {@code in} with a buffer
   * of 8192 bytes.
   *
   * <p><strong>Warning:</strong> passing a null source creates a closed
   * {@code BufferedInputStream}. All read operations on such a stream will
   * fail with an IOException.
   *
   * @param in the {@code InputStream} the buffer reads from.
   */
  public UnsynchronizedBufferedInputStream(InputStream in) {
    this(in, DEFAULT_BUFFER_SIZE);
  }

  /**
   * Constructs a new {@code BufferedInputStream}, providing {@code in} with {@code size} bytes
   * of buffer.
   *
   * <p><strong>Warning:</strong> passing a null source creates a closed
   * {@code BufferedInputStream}. All read operations on such a stream will
   * fail with an IOException.
   *
   * @param in the {@code InputStream} the buffer reads from.
   * @param size the size of buffer in bytes.
   * @throws IllegalArgumentException if {@code size <= 0}.
   */
  public UnsynchronizedBufferedInputStream(InputStream in, int size) {
    this.in = in;
    if (size <= 0) {
      throw new IllegalArgumentException("size <= 0");
    }
    buf = new byte[size];
  }

  /**
   * Returns an estimated number of bytes that can be read or skipped without blocking for more
   * input. This method returns the number of bytes available in the buffer
   * plus those available in the source stream, but see {@link InputStream#available} for
   * important caveats.
   *
   * @return the estimated number of bytes available
   * @throws IOException if this stream is closed or an error occurs
   */
  @Override
  public int available() throws IOException {
    if (buf == null || in == null) {
      throw streamClosed();
    }
    return count - pos + in.available();
  }

  private static IOException streamClosed() {
    return new IOException("BufferedInputStream is closed");
  }

  /**
   * Closes this stream. The source stream is closed and any resources
   * associated with it are released.
   *
   * @throws IOException
   *             if an error occurs while closing this stream.
   */
  @Override
  public void close() throws IOException {
    buf = null;
    InputStream localIn = in;
    in = null;
    if (localIn != null) {
      localIn.close();
    }
  }

  private int fillbuf() throws IOException {
    if (markpos == -1 || (pos - markpos >= marklimit)) {
      /* Mark position not set or exceeded readlimit */
      int result = in.read(buf);
      if (result > 0) {
        markpos = -1;
        pos = 0;
        count = result;
      }
      return result;
    }
    if (markpos == 0 && marklimit > buf.length) {
      /* Increase buffer size to accommodate the readlimit */
      int newLength = buf.length * 2;
      if (newLength > marklimit) {
        newLength = marklimit;
      }
      byte[] newbuf = new byte[newLength];
      System.arraycopy(buf, 0, newbuf, 0, count);
      buf = newbuf;
    } else if (markpos > 0) {
      System.arraycopy(buf, markpos, buf, 0, count - markpos);
    }
    /* Set the new position and mark position */
    pos -= markpos;
    count = markpos = 0;
    int bytesread = in.read(buf, pos, buf.length - pos);
    count = bytesread <= 0 ? pos : pos + bytesread;
    return bytesread;
  }

  /**
   * Sets a mark position in this stream. The parameter {@code readlimit}
   * indicates how many bytes can be read before a mark is invalidated.
   * Calling {@code reset()} will reposition the stream back to the marked
   * position if {@code readlimit} has not been surpassed. The underlying
   * buffer may be increased in size to allow {@code readlimit} number of
   * bytes to be supported.
   *
   * @param readlimit
   *            the number of bytes that can be read before the mark is
   *            invalidated.
   * @see #reset()
   */
  @Override
  public void mark(int readlimit) {
    marklimit = readlimit;
    markpos = pos;
  }

  /**
   * Indicates whether {@code BufferedInputStream} supports the {@code mark()}
   * and {@code reset()} methods.
   *
   * @return {@code true} for BufferedInputStreams.
   * @see #mark(int)
   * @see #reset()
   */
  @Override
  public boolean markSupported() {
    return true;
  }

  /**
   * Reads a single byte from this stream and returns it as an integer in the
   * range from 0 to 255. Returns -1 if the end of the source string has been
   * reached. If the internal buffer does not contain any available bytes then
   * it is filled from the source stream and the first byte is returned.
   *
   * @return the byte read or -1 if the end of the source stream has been
   *         reached.
   * @throws IOException
   *             if this stream is closed or another IOException occurs.
   */
  @Override
  public int read() throws IOException {
    if (buf == null || in == null) {
      throw streamClosed();
    }

    /* Are there buffered bytes available? */
    if (pos >= count && fillbuf() == -1) {
      return -1; /* no, fill buffer */
    }

    /* Did filling the buffer fail with -1 (EOF)? */
    if (count - pos > 0) {
      return buf[pos++] & 0xFF;
    }
    return -1;
  }

  @Override
  public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
    if (buf == null) {
      throw streamClosed();
    }

    if ((byteOffset | byteCount) < 0 || byteCount > buffer.length - byteOffset) {
      throw new ArrayIndexOutOfBoundsException("length=" + buffer.length + "; regionStart=" + byteOffset
          + "; regionLength=" + byteCount);
    }

    if (byteCount == 0) {
      return 0;
    }

    if (in == null) {
      throw streamClosed();
    }

    int required;
    if (pos < count) {
      /* There are bytes available in the buffer. */
      int copylength = Math.min(count - pos, byteCount);
      System.arraycopy(buf, pos, buffer, byteOffset, copylength);
      pos += copylength;
      if (copylength == byteCount || in.available() == 0) {
        return copylength;
      }
      byteOffset += copylength;
      required = byteCount - copylength;
    } else {
      required = byteCount;
    }

    while (true) {
      int read;

      boolean canBypassBuffer = ((markpos == -1) || (pos - markpos >= marklimit));
      /*
       * If we're not marked and the required size is greater than the
       * buffer, simply read the bytes directly bypassing the buffer.
       */
      if (canBypassBuffer && required >= buf.length) {
        read = in.read(buffer, byteOffset, required);
        if (read == -1) {
          return required == byteCount ? -1 : byteCount - required;
        }
        markpos = -1;
      } else {
        if (fillbuf() == -1) {
          return required == byteCount ? -1 : byteCount - required;
        }

        read = Math.min(count - pos, required);
        System.arraycopy(buf, pos, buffer, byteOffset, read);
        pos += read;
      }
      required -= read;
      if (required == 0) {
        return byteCount;
      }
      if (in.available() == 0) {
        return byteCount - required;
      }
      byteOffset += read;
    }
  }

  /**
   * Resets this stream to the last marked location.
   *
   * @throws IOException
   *             if this stream is closed, no mark has been set or the mark is
   *             no longer valid because more than {@code readlimit} bytes
   *             have been read since setting the mark.
   * @see #mark(int)
   */
  @Override
  public void reset() throws IOException {
    if (buf == null) {
      throw new IOException("Stream is closed");
    }
    if (markpos == -1) {
      throw new IOException("Mark has been invalidated.");
    }
    pos = markpos;
  }

  /**
   * Skips {@code byteCount} bytes in this stream. Subsequent calls to
   * {@code read} will not return these bytes unless {@code reset} is
   * used.
   *
   * @param byteCount
   *            the number of bytes to skip. {@code skip} does nothing and
   *            returns 0 if {@code byteCount} is less than zero.
   * @return the number of bytes actually skipped.
   * @throws IOException
   *             if this stream is closed or another IOException occurs.
   */
  @Override
  public long skip(long byteCount) throws IOException {
    if (buf == null) {
      throw streamClosed();
    }
    if (byteCount < 1) {
      return 0;
    }
    if (in == null) {
      throw streamClosed();
    }

    long read = count - pos;
    if (read >= byteCount) {
      pos += byteCount;
      return byteCount;
    }
    pos = count;

    if ((markpos == -1) || (count - markpos >= marklimit)) {
      long skipped = in.skip(byteCount - read);
      if (skipped > 0) {
        markpos = -1;
      }
      return read + skipped;
    }

    if (fillbuf() == -1) {
      return read;
    }
    if (count - pos >= byteCount - read) {
      pos += byteCount - read;
      return byteCount;
    }
    // Couldn't get all the bytes, skip what we read
    read += (count - pos);
    pos = count;
    return read;
  }
}
