package infrastructure.vault;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PostgreSqlTlsTest {
  private static final String URL =
      "jdbc:postgresql://postgres-identity:5432/payguard_identity?sslmode=verify-full&sslrootcert=/run/ca.crt";

  @Test
  void duplicateOrEncodedOverridesAndCustomVerifiersCannotDisableCertificateChecks() {
    assertDoesNotThrow(() -> PostgreSqlTls.requireVerified(URL));
    for (String override :
        new String[] {
          "sslmode=disable",
          "ssl%6dode=disable",
          "password=synthetic-marker",
          "sslfactory=org.postgresql.ssl.NonValidatingFactory",
          "sslhostnameverifier=custom.Verifier"
        }) {
      String configured = URL + "&" + override;
      assertThrows(IllegalArgumentException.class, () -> PostgreSqlTls.requireVerified(configured));
    }
    String disabled = URL.replace("verify-full", "require");
    assertThrows(IllegalArgumentException.class, () -> PostgreSqlTls.requireVerified(disabled));
  }
}
