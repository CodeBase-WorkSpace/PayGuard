package dev.amg.payguard.gateway.web;

import dev.amg.payguard.proto.common.v1.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class GatewayRequestContextFactory {
  public RequestContext create(HttpServletRequest request, String idempotencyKey) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String organizationId = claim(authentication, "organization_id");
    if (organizationId.isBlank()) {
      organizationId = claim(authentication, "org_id");
    }
    if (organizationId.isBlank() && !(authentication instanceof JwtAuthenticationToken)) {
      organizationId = request.getHeader("X-Organization-Id");
    }
    String subjectId = authentication == null ? "" : safe(authentication.getName());
    String correlationId = headerOrGenerated(request, "X-Correlation-Id");
    String causationId = safe(request.getHeader("X-Causation-Id"));
    return RequestContext.newBuilder()
        .setCorrelationId(correlationId)
        .setCausationId(causationId)
        .setOrganizationId(organizationId)
        .setSubjectId(subjectId)
        .setIdempotencyKey(safe(idempotencyKey))
        .build();
  }

  private String claim(Authentication authentication, String name) {
    if (authentication instanceof JwtAuthenticationToken jwt) {
      Object value = jwt.getToken().getClaims().get(name);
      return value == null ? "" : safe(value.toString());
    }
    return "";
  }

  private String headerOrGenerated(HttpServletRequest request, String name) {
    String value = safe(request.getHeader(name));
    return value.isBlank() ? UUID.randomUUID().toString() : value;
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
