package dev.amg.payguard.identity.verification.application.port;

@FunctionalInterface
public interface VerificationAudit {
  /** Persists synchronously; a persistence failure propagates to the caller. */
  void recordAudit(VerificationAuditEntry entry);
}
