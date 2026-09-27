package dev.amg.payguard.gateway.client;

import dev.amg.payguard.proto.common.v1.RequestContext;
import dev.amg.payguard.proto.loan.v1.GetLoanRequest;
import dev.amg.payguard.proto.loan.v1.LoanCommandRequest;
import dev.amg.payguard.proto.loan.v1.LoanResponse;
import dev.amg.payguard.proto.loan.v1.LoanServiceGrpc;
import dev.amg.payguard.proto.loan.v1.RepaymentRequest;
import dev.amg.payguard.proto.loan.v1.RepaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class LoanGatewayClient {
  private final LoanServiceGrpc.LoanServiceBlockingStub loan;

  public LoanGatewayClient(LoanServiceGrpc.LoanServiceBlockingStub loan) {
    this.loan = loan;
  }

  public LoanResponse get(String loanId, RequestContext context) {
    return loan.getLoan(GetLoanRequest.newBuilder().setLoanId(loanId).setContext(context).build());
  }

  public LoanResponse approve(String loanId, RequestContext context) {
    return loan.approveLoan(
        LoanCommandRequest.newBuilder().setLoanId(loanId).setContext(context).build());
  }

  public RepaymentResponse repay(RepaymentRequest request) {
    return loan.createRepayment(request);
  }
}
