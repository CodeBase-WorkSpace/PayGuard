package dev.amg.payguard.infrastructure.aop.exception.model;

import java.time.Instant;

public record UnifiedResponse<T>(
    boolean success, String errorCode, String message, T data, Instant timestamp) {
  public static <T> UnifiedResponse<T> ok(T data) {
    return new UnifiedResponse<>(true, null, "Operation successful", data, Instant.now());
  }

  public static <T> UnifiedResponse<T> fail(String errorCode, String message) {
    return new UnifiedResponse<>(false, errorCode, message, null, Instant.now());
  }
}
