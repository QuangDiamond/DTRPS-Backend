package com.dtrps.exception;

import org.springframework.http.HttpStatus;

/** The message is returned to the client, so it must never contain internal details. */
public class ApiException extends RuntimeException {
  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
