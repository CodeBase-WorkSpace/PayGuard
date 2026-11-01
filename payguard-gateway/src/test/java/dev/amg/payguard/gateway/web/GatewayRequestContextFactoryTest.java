package dev.amg.payguard.gateway.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GatewayRequestContextFactoryTest {
  @Test
  void createsCorrelationAndTenantContextForLocalRequests() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "corr-1");
    request.addHeader("X-Organization-Id", "org-1");

    var context = new GatewayRequestContextFactory().create(request, "idem-1");

    assertEquals("corr-1", context.getCorrelationId());
    assertEquals("org-1", context.getOrganizationId());
    assertEquals("idem-1", context.getIdempotencyKey());
  }
}
