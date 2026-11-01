package dev.amg.payguard.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payguard.gateway")
public class GatewayProperties {
  private GrpcTarget wallet = new GrpcTarget("localhost:9091", true);
  private GrpcTarget loan = new GrpcTarget("localhost:9092", true);
  private GrpcTarget collateral = new GrpcTarget("localhost:9093", true);

  public GrpcTarget getWallet() {
    return wallet;
  }

  public void setWallet(GrpcTarget wallet) {
    this.wallet = wallet;
  }

  public GrpcTarget getLoan() {
    return loan;
  }

  public void setLoan(GrpcTarget loan) {
    this.loan = loan;
  }

  public GrpcTarget getCollateral() {
    return collateral;
  }

  public void setCollateral(GrpcTarget collateral) {
    this.collateral = collateral;
  }

  public record GrpcTarget(String target, boolean plaintext) {}
}
