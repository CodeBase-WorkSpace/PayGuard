package dev.amg.payguard.identity.verification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.infrastructure.security.OtpIdGenerator;
import org.junit.jupiter.api.Test;

class TypedIdentifiersTest {
  @Test
  void acceptsExactFormatAndPreservesCaseAndEquality() {
    String value = "cus_aBCDEFGHIJKLMNOPQRSTUV";
    CustomerId customer = CustomerId.parse(value);
    assertEquals(value, customer.getValue());
    assertEquals(customer, CustomerId.parse(value));
    assertEquals(customer.hashCode(), CustomerId.parse(value).hashCode());
    assertNotEquals(customer, CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"));
    assertFalse(customer.sameValueAs(OtpId.parse("otp_aBCDEFGHIJKLMNOPQRSTUV")));
  }

  @Test
  void rejectsWrongPrefixesLengthsWhitespaceAndNonAscii() {
    for (String value :
        new String[] {
          "xxx_ABCDEFGHIJKLMNOPQRSTUV",
          "cus_ABCDEFGHIJKLMNOPQRSTU",
          "cus_ABCDEFGHIJKLMNOPQRSTUVW",
          "cus_ABCDEFGHIJKLMNOPQRSTU ",
          "cus_ABCDEFGHIJKLMNOPQRSTUé",
          ""
        }) {
      assertThrows(IllegalArgumentException.class, () -> CustomerId.parse(value));
    }
    assertThrows(NullPointerException.class, () -> CustomerId.parse(null));
    assertThrows(IllegalArgumentException.class, () -> OtpId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"));
  }

  @Test
  void generatorProducesAcceptedTypedIdsWithoutUuidConversion() {
    OtpIdGenerator generator = new OtpIdGenerator();
    OtpId first = generator.generate();
    assertTrue(first.value().matches("otp_[A-Za-z0-9]{22}"));
    assertEquals(first, OtpId.parse(first.value()));
    assertNotEquals(first, generator.generate());
  }
}
