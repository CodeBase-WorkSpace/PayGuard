package dev.amg.payguard.gateway.config;

import dev.amg.payguard.proto.collateral.v1.CollateralServiceGrpc;
import dev.amg.payguard.proto.loan.v1.LoanServiceGrpc;
import dev.amg.payguard.proto.wallet.v1.WalletServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GrpcClientConfiguration {
  @Bean(destroyMethod = "shutdownNow")
  ManagedChannel walletChannel(GatewayProperties properties) {
    return channel(properties.getWallet());
  }

  @Bean(destroyMethod = "shutdownNow")
  ManagedChannel loanChannel(GatewayProperties properties) {
    return channel(properties.getLoan());
  }

  @Bean(destroyMethod = "shutdownNow")
  ManagedChannel collateralChannel(GatewayProperties properties) {
    return channel(properties.getCollateral());
  }

  @Bean
  WalletServiceGrpc.WalletServiceBlockingStub walletStub(
      @Qualifier("walletChannel") ManagedChannel walletChannel) {
    return WalletServiceGrpc.newBlockingStub(walletChannel);
  }

  @Bean
  LoanServiceGrpc.LoanServiceBlockingStub loanStub(
      @Qualifier("loanChannel") ManagedChannel loanChannel) {
    return LoanServiceGrpc.newBlockingStub(loanChannel);
  }

  @Bean
  CollateralServiceGrpc.CollateralServiceBlockingStub collateralStub(
      @Qualifier("collateralChannel") ManagedChannel collateralChannel) {
    return CollateralServiceGrpc.newBlockingStub(collateralChannel);
  }

  private ManagedChannel channel(GatewayProperties.GrpcTarget target) {
    ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target.target());
    if (target.plaintext()) {
      builder.usePlaintext();
    }
    return builder.build();
  }
}
