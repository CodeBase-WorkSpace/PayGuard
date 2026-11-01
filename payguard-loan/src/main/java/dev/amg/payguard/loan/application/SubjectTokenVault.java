package dev.amg.payguard.loan.application;

/** Boundary for KMS-backed encryption of the service-local linkage token. */
public interface SubjectTokenVault {
  String encrypt(String subjectId);

  String decrypt(String encryptedToken);
}
