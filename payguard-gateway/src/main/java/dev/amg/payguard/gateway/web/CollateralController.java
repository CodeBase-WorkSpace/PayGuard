package dev.amg.payguard.gateway.web;

import dev.amg.payguard.gateway.client.CollateralGatewayClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/collateral", produces = MediaType.APPLICATION_JSON_VALUE)
public class CollateralController {
  private final CollateralGatewayClient client;
  private final GatewayRequestContextFactory contexts;
  private final GatewayJson json;

  public CollateralController(
      CollateralGatewayClient client, GatewayRequestContextFactory contexts, GatewayJson json) {
    this.client = client;
    this.contexts = contexts;
    this.json = json;
  }

  @GetMapping("/{positionId}")
  public Object get(@PathVariable String positionId, HttpServletRequest request) {
    return json.fromProto(client.get(positionId, contexts.create(request, null)));
  }

  @PostMapping("/refresh-prices")
  public Object refresh(HttpServletRequest request) {
    return json.fromProto(client.refresh(contexts.create(request, null)));
  }
}
