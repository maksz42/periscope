package com.maksz42.periscope.utils;

@FunctionalInterface
public interface ThrowingAction {
  void run() throws Exception;

  default Runnable toUnchecked() {
    return () -> {
      try {
        run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }
}
