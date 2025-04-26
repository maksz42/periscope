package com.maksz42.periscope.io;

import java.io.ByteArrayOutputStream;

public class FastBAOS extends ByteArrayOutputStream {
  public FastBAOS() {
    super();
  }

  public FastBAOS(int size) {
    super(size);
  }

  public byte[] getBuffer() {
    return buf;
  }
}
