package dev.amg.payguard.gateway.client;

import dev.amg.payguard.proto.collateral.v1.CollateralResponse;
import dev.amg.payguard.proto.collateral.v1.CollateralServiceGrpc;
import dev.amg.payguard.proto.collateral.v1.GetPositionRequest;
import dev.amg.payguard.proto.collateral.v1.RefreshPricesRequest;
import dev.amg.payguard.proto.collateral.v1.RefreshPricesResponse;
import dev.amg.payguard.proto.common.v1.RequestContext;
import org.springframework.stereotype.Component;

@Component
public class CollateralGatewayClient {
  private final CollateralServiceGrpc.CollateralServiceBlockingStub collateral;

  public CollateralGatewayClient(CollateralServiceGrpc.CollateralServiceBlockingStub collateral) {
    this.collateral = collateral;
  }

  public CollateralResponse get(String positionId, RequestContext context) {
    return collateral.getPosition(
        GetPositionRequest.newBuilder().setPositionId(positionId).setContext(context).build());
  }

  public RefreshPricesResponse refresh(RequestContext context) {
    return collateral.refreshPrices(RefreshPricesRequest.newBuilder().setContext(context).build());
  }
}
