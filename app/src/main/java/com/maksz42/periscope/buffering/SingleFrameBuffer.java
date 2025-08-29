package com.maksz42.periscope.buffering;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;

public class SingleFrameBuffer extends FrameBuffer {
  public static class FrameReadyGate {
    private volatile boolean ready = false;
    private final Condition cond;

    private FrameReadyGate(Condition cond) {
      this.cond = cond;
    }
  }

  private volatile Bitmap bitmap;
  private volatile FrameReadyGate frameReadyGate;
  private final boolean shouldSignalFrameReady;

  public SingleFrameBuffer(boolean shouldSignalFrameReady) {
    this.shouldSignalFrameReady = shouldSignalFrameReady;
    newConsumer();
  }

  public void newConsumer() {
    frameReadyGate = new FrameReadyGate(lock.newCondition());
  }

  public FrameReadyGate getFrameReadyGate() {
    return frameReadyGate;
  }

  public void setFrameReadyGate(FrameReadyGate gate) {
    frameReadyGate = gate;
  }

  public void lockInterruptibly() throws InterruptedException {
    lock.lockInterruptibly();
  }

  public void awaitFrameReady() throws InterruptedException {
    FrameReadyGate gate = frameReadyGate;
    while (!gate.ready) {
      gate.cond.await();
    }
    gate.ready = false;
  }

  private void signalFrameReady() {
    frameReadyGate.ready = true;
    frameReadyGate.cond.signal();
  }

  public void signalFrameReadyNonBlocking() {
    if (lock.tryLock()) {
      try {
        signalFrameReady();
      } finally {
        lock.unlock();
      }
    }
  }

  private void signalFrameReadyIfNeeded() {
    if (shouldSignalFrameReady) {
      signalFrameReady();
    }
  }

  @Override
  public void decodeStream(InputStream input) throws IOException {
    if (SUPPORTS_REUSING_BITMAP) {
      decodeStreamModern(input);
    } else {
      decodeStreamAncient(input);
    }
  }

  private void decodeStreamModern(InputStream input) throws IOException {
    lock.lock();
    try {
      bitmap = decodeStream(input, bitmap);
      signalFrameReadyIfNeeded();
    } finally {
      lock.unlock();
    }
  }

  private void decodeStreamAncient(InputStream input) throws IOException {
    Bitmap oldBitmap = bitmap;
    bitmap = decodeStream(input, oldBitmap);
    if (bitmap != null) {
      lock.lock();
      try {
        signalFrameReadyIfNeeded();
        // https://developer.android.com/topic/performance/graphics/manage-memory#recycle
        oldBitmap.recycle();
      } finally {
        lock.unlock();
      }
    }
  }

  @Override
  public Bitmap getFrame() {
    return bitmap;
  }
}
