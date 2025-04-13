package com.maksz42.periscope.frigate;

public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() {
    super();
  }

  public InvalidCredentialsException(String message) {
    super(message);
  }

  public InvalidCredentialsException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidCredentialsException(Throwable cause) {
    super(cause);
  }
}
