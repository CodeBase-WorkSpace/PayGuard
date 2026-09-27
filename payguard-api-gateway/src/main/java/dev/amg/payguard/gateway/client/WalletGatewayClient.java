package dev.amg.payguard.gateway.client;

import dev.amg.payguard.proto.common.v1.RequestContext;
import dev.amg.payguard.proto.wallet.v1.BalanceResponse;
import dev.amg.payguard.proto.wallet.v1.CreateWalletRequest;
import dev.amg.payguard.proto.wallet.v1.FinancialOperationResponse;
import dev.amg.payguard.proto.wallet.v1.GetWalletRequest;
import dev.amg.payguard.proto.wallet.v1.ListTransactionsRequest;
import dev.amg.payguard.proto.wallet.v1.ReferenceOperationRequest;
import dev.amg.payguard.proto.wallet.v1.ReservationRequest;
import dev.amg.payguard.proto.wallet.v1.ReservationResponse;
import dev.amg.payguard.proto.wallet.v1.TransactionPageResponse;
import dev.amg.payguard.proto.wallet.v1.TransferRequest;
import dev.amg.payguard.proto.wallet.v1.WalletResponse;
import dev.amg.payguard.proto.wallet.v1.WalletServiceGrpc;
import org.springframework.stereotype.Component;

@Component
public class WalletGatewayClient {
  private final WalletServiceGrpc.WalletServiceBlockingStub wallet;

  public WalletGatewayClient(WalletServiceGrpc.WalletServiceBlockingStub wallet) {
    this.wallet = wallet;
  }

  public WalletResponse create(CreateWalletRequest request) {
    return wallet.createWallet(request);
  }

  public WalletResponse get(String walletId, RequestContext context) {
    return wallet.getWallet(
        GetWalletRequest.newBuilder().setWalletId(walletId).setContext(context).build());
  }

  public BalanceResponse balance(String walletId, RequestContext context) {
    return wallet.getBalance(
        GetWalletRequest.newBuilder().setWalletId(walletId).setContext(context).build());
  }

  public TransactionPageResponse transactions(ListTransactionsRequest request) {
    return wallet.listTransactions(request);
  }

  public FinancialOperationResponse credit(dev.amg.payguard.proto.wallet.v1.CreditRequest request) {
    return wallet.credit(request);
  }

  public FinancialOperationResponse debit(dev.amg.payguard.proto.wallet.v1.DebitRequest request) {
    return wallet.debit(request);
  }

  public FinancialOperationResponse transfer(TransferRequest request) {
    return wallet.transfer(request);
  }

  public FinancialOperationResponse refund(ReferenceOperationRequest request) {
    return wallet.refund(request);
  }

  public FinancialOperationResponse reverse(ReferenceOperationRequest request) {
    return wallet.reverse(request);
  }

  public ReservationResponse reserve(ReservationRequest request) {
    return wallet.createReservation(request);
  }

  public FinancialOperationResponse capture(ReferenceOperationRequest request) {
    return wallet.captureReservation(request);
  }

  public FinancialOperationResponse release(ReferenceOperationRequest request) {
    return wallet.releaseReservation(request);
  }
}
