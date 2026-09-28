package dev.amg.payguard.gateway.web;

import dev.amg.payguard.gateway.client.LoanGatewayClient;
import dev.amg.payguard.proto.common.v1.Money;
import dev.amg.payguard.proto.loan.v1.RepaymentRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/loans", produces = MediaType.APPLICATION_JSON_VALUE)
public class LoanController {
  private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  private final LoanGatewayClient client;
  private final GatewayRequestContextFactory contexts;
  private final GatewayJson json;

  public LoanController(
      LoanGatewayClient client, GatewayRequestContextFactory contexts, GatewayJson json) {
    this.client = client;
    this.contexts = contexts;
    this.json = json;
  }

  @GetMapping("/{loanId}")
  public Object get(@PathVariable String loanId, HttpServletRequest request) {
    return json.fromProto(client.get(loanId, contexts.create(request, null)));
  }

  @PostMapping("/{loanId}/approve")
  public Object approve(
      @PathVariable String loanId,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    return json.fromProto(client.approve(loanId, contexts.create(request, key)));
  }

  @PostMapping("/{loanId}/repayments")
  public Object repay(
      @PathVariable String loanId,
      @Valid @RequestBody RepaymentBody body,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    var requestMessage =
        RepaymentRequest.newBuilder()
            .setLoanId(loanId)
            .setContext(contexts.create(request, key))
            .setAmount(
                Money.newBuilder().setAmount(body.amount()).setCurrency(body.currency()).build())
            .build();
    return json.fromProto(client.repay(requestMessage));
  }

  public record RepaymentBody(@NotBlank String amount, @NotBlank String currency) {}
}
