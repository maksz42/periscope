package com.maksz42.periscope.overlay;

/**
 * How old the newest picture is, and whether it is too old to pass for live. Pure logic, so it
 * can be tested without a device. Times are from a monotonic clock (elapsedRealtime), so changing
 * the TV's clock does not change ages.
 *
 * Only a picture that differs from the previous one counts as new: when a camera stops, Frigate
 * keeps answering with its last picture (or a placeholder), and a successful download alone
 * would make that look live.
 */
final class FrameStatus {
  /** A frame older than this is shown as stale, never as live. */
  static final long STALE_MS = 3000;

  private long lastFrameAt = -1;
  private long lastChecksum;
  private String lastError;

  /**
   * A picture arrived. Returns true if it differs from the previous one, which is the only kind
   * that resets the age.
   */
  boolean frame(long now, long checksum) {
    lastError = null;
    if (lastFrameAt >= 0 && checksum == lastChecksum) return false;
    lastChecksum = checksum;
    lastFrameAt = now;
    return true;
  }

  void error(String message) {
    lastError = message;
  }

  boolean hasFrame() {
    return lastFrameAt >= 0;
  }

  /** Milliseconds since the newest frame; meaningless before the first one. */
  long ageMs(long now) {
    return now - lastFrameAt;
  }

  /** The last fetch error since the newest frame, or null. */
  String error() {
    return lastError;
  }

  boolean stale(long now) {
    return lastFrameAt < 0 || now - lastFrameAt > STALE_MS;
  }

  /**
   * Whether the label should warn: the picture is too old, or no picture could be fetched. A
   * window still waiting for its first picture is not a warning, so opening a camera doesn't
   * flash red.
   */
  boolean warning(long now) {
    return hasFrame() ? stale(now) : lastError != null;
  }

  /**
   * Each window session has a number; a fetch started for an older session must not draw into
   * a newer one or into a closed window.
   */
  static final class Generation {
    private int current;

    int next() {
      return ++current;
    }

    boolean isCurrent(int generation) {
      return generation == current;
    }

    void invalidate() {
      current++;
    }
  }
}
