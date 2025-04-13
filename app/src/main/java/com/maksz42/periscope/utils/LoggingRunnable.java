package com.maksz42.periscope.utils;

@FunctionalInterface
public interface LoggingRunnable extends Runnable {
  @Override
  default void run() {
    try {
      throwingRun();
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  void throwingRun() throws RuntimeException;
}
