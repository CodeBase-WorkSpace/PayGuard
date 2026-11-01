package dev.amg.payguard.gateway.web;

import java.io.Serial;

public final class GatewaySerializationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public GatewaySerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
