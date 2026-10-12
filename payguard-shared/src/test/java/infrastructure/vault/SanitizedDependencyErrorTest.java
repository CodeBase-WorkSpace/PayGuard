package infrastructure.vault;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class SanitizedDependencyErrorTest {
  @Test
  void renderedStackTraceKeepsFramesWithoutOriginalSecretMessagesOrNestedCauses() {
    var original =
        new SQLException(
            "synthetic-password-error-marker",
            new IllegalArgumentException("synthetic-token-error-marker"));
    var safe = new VaultUnavailableException(original);
    var rendered = new StringWriter();
    safe.printStackTrace(new PrintWriter(rendered));
    assertFalse(rendered.toString().contains("synthetic-password-error-marker"));
    assertFalse(rendered.toString().contains("synthetic-token-error-marker"));
    assertTrue(rendered.toString().contains("SanitizedDependencyErrorTest"));
  }
}
