package dev.amg.payguard.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amg.payguard.proto.common.v1.RequestContext;
import dev.amg.payguard.proto.wallet.v1.WalletServiceGrpc;
import org.junit.jupiter.api.Test;

class ProtoContractTests {
  @Test
  void exposesVersionedWalletGrpcServiceAndTenantContext() {
    RequestContext context =
        RequestContext.newBuilder()
            .setOrganizationId("org-1")
            .setCorrelationId("corr-1")
            .setIdempotencyKey("idem-1")
            .build();

    assertEquals("org-1", context.getOrganizationId());
    assertTrue(
        WalletServiceGrpc.getCreateWalletMethod().getFullMethodName().contains("WalletService"));
  }
}
