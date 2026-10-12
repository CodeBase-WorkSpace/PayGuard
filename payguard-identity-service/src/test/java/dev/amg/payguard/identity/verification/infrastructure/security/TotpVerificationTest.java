package dev.amg.payguard.identity.verification.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TotpVerificationTest {
  private static final CustomerId CUSTOMER = CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV");

  @Test
  void verifiesKnownRfc6238Sha1VectorWithExistingSixDigitFormat() {
    var verifier =
        new TotpAuthenticatorCodeVerifier(
            customer -> Optional.of("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"));
    assertTrue(verifier.verify(CUSTOMER, "287082", Instant.ofEpochSecond(59)));
    assertFalse(verifier.verify(CUSTOMER, "000000", Instant.ofEpochSecond(59)));
    assertFalse(verifier.verify(CUSTOMER, "287082", Instant.ofEpochSecond(300)));
    assertFalse(verifier.verify(CUSTOMER, "2870820", Instant.ofEpochSecond(59)));
  }

  @Test
  void missingOrInvalidSecretsFailClosed() {
    var missing = new TotpAuthenticatorCodeVerifier(new UnavailableAuthenticatorSecretAdapter());
    assertFalse(missing.verify(CUSTOMER, "287082", Instant.ofEpochSecond(59)));
    var invalid = new TotpAuthenticatorCodeVerifier(customer -> Optional.of("invalid!"));
    assertFalse(invalid.verify(CUSTOMER, "287082", Instant.ofEpochSecond(59)));
  }
}
