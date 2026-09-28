package dev.amg.payguard.gateway.web;

import dev.amg.payguard.gateway.client.WalletGatewayClient;
import dev.amg.payguard.proto.common.v1.Money;
import dev.amg.payguard.proto.wallet.v1.CreditRequest;
import dev.amg.payguard.proto.wallet.v1.DebitRequest;
import dev.amg.payguard.proto.wallet.v1.ListTransactionsRequest;
import dev.amg.payguard.proto.wallet.v1.ReferenceOperationRequest;
import dev.amg.payguard.proto.wallet.v1.ReservationRequest;
import dev.amg.payguard.proto.wallet.v1.TransferRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class WalletController {
  private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  private final WalletGatewayClient client;
  private final GatewayRequestContextFactory contexts;
  private final GatewayJson json;

  public WalletController(
      WalletGatewayClient client, GatewayRequestContextFactory contexts, GatewayJson json) {
    this.client = client;
    this.contexts = contexts;
    this.json = json;
  }

  @PostMapping("/wallets")
  public ResponseEntity<?> create(
      @Valid @RequestBody WalletRequests.CreateWalletRequest body,
      HttpServletRequest request,
      @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
    var context = contexts.create(request, idempotencyKey);
    var result =
        client.create(
            dev.amg.payguard.proto.wallet.v1.CreateWalletRequest.newBuilder()
                .setContext(context)
                .setExternalCustomerId(body.externalCustomerId())
                .setCurrency(body.currency())
                .build());
    return ResponseEntity.status(201).body(json.fromProto(result));
  }

  @GetMapping("/wallets/{walletId}")
  public Object get(@PathVariable String walletId, HttpServletRequest request) {
    return json.fromProto(client.get(walletId, contexts.create(request, null)));
  }

  @GetMapping("/wallets/{walletId}/balance")
  public Object balance(@PathVariable String walletId, HttpServletRequest request) {
    return json.fromProto(client.balance(walletId, contexts.create(request, null)));
  }

  @GetMapping("/wallets/{walletId}/transactions")
  public Object transactions(
      @PathVariable String walletId,
      HttpServletRequest request,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(defaultValue = "created_at") String sort,
      @RequestParam(defaultValue = "DESC") String direction,
      @RequestParam Optional<String> transactionType) {
    var context = contexts.create(request, null);
    var pageRequest =
        dev.amg.payguard.proto.common.v1.PageRequest.newBuilder()
            .setPage(Math.max(page, 0))
            .setSize(Math.min(Math.max(size, 1), 100))
            .setSort(sort)
            .setDirection(direction)
            .build();
    var grpcRequest =
        ListTransactionsRequest.newBuilder()
            .setContext(context)
            .setWalletId(walletId)
            .setPage(pageRequest)
            .setTransactionType(transactionType.orElse(""))
            .build();
    return json.fromProto(client.transactions(grpcRequest));
  }

  @PostMapping("/wallets/{walletId}/credits")
  public Object credit(
      @PathVariable String walletId,
      @Valid @RequestBody WalletRequests.OperationRequest body,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    return json.fromProto(
        client.credit(
            CreditRequest.newBuilder()
                .setContext(contexts.create(request, key))
                .setWalletId(walletId)
                .setAmount(money(body.amount(), body.currency()))
                .setTransactionType(value(body.transactionType()))
                .setReferenceId(value(body.referenceId()))
                .setDescription(value(body.description()))
                .build()));
  }

  @PostMapping("/wallets/{walletId}/debits")
  public Object debit(
      @PathVariable String walletId,
      @Valid @RequestBody WalletRequests.OperationRequest body,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    return json.fromProto(
        client.debit(
            DebitRequest.newBuilder()
                .setContext(contexts.create(request, key))
                .setWalletId(walletId)
                .setAmount(money(body.amount(), body.currency()))
                .setTransactionType(value(body.transactionType()))
                .setReferenceId(value(body.referenceId()))
                .setDescription(value(body.description()))
                .build()));
  }

  @PostMapping("/transfers")
  public Object transfer(
      @Valid @RequestBody WalletRequests.TransferRequest body,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    return json.fromProto(
        client.transfer(
            TransferRequest.newBuilder()
                .setContext(contexts.create(request, key))
                .setSourceWalletId(body.sourceWalletId())
                .setDestinationWalletId(body.destinationWalletId())
                .setAmount(money(body.amount(), body.currency()))
                .setReferenceId(value(body.referenceId()))
                .setDescription(value(body.description()))
                .build()));
  }

  @PostMapping("/transactions/{transactionId}/refund")
  public Object refund(
      @PathVariable String transactionId,
      @RequestBody(required = false) WalletRequests.ReferenceRequest body,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    return json.fromProto(
        client.refund(reference(transactionId, body, contexts.create(request, key))));
  }

  @PostMapping("/transactions/{transactionId}/reverse")
  public Object reverse(
      @PathVariable String transactionId,
      @RequestBody(required = false) WalletRequests.ReferenceRequest body,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    return json.fromProto(
        client.reverse(reference(transactionId, body, contexts.create(request, key))));
  }

  @PostMapping("/wallets/{walletId}/holds")
  public Object reserve(
      @PathVariable String walletId,
      @Valid @RequestBody WalletRequests.OperationRequest body,
      HttpServletRequest request,
      @RequestHeader(IDEMPOTENCY_KEY_HEADER) String key) {
    var grpc =
        ReservationRequest.newBuilder()
            .setContext(contexts.create(request, key))
            .setWalletId(walletId)
            .setAmount(money(body.amount(), body.currency()))
            .setReferenceId(value(body.referenceId()))
            .setDescription(value(body.description()))
            .build();
    return json.fromProto(client.reserve(grpc));
  }

  private ReferenceOperationRequest reference(
      String id,
      WalletRequests.ReferenceRequest body,
      dev.amg.payguard.proto.common.v1.RequestContext context) {
    return ReferenceOperationRequest.newBuilder()
        .setContext(context)
        .setTransactionId(id)
        .setReferenceId(body == null ? "" : value(body.referenceId()))
        .setDescription(body == null ? "" : value(body.description()))
        .build();
  }

  private Money money(String amount, String currency) {
    return Money.newBuilder().setAmount(amount).setCurrency(currency).build();
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
