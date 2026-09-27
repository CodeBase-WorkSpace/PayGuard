package dev.amg.payguard.gateway.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class WalletRequests {
  public static final String CURRENCY_PATTERN = "[A-Z]{3}";

  private WalletRequests() {}

  public record CreateWalletRequest(
      @NotBlank String externalCustomerId,
      @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String currency) {}

  public record MoneyRequest(
      @NotBlank String amount, @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String currency) {}

  public record OperationRequest(
      @NotBlank String amount,
      @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String currency,
      String transactionType,
      String referenceId,
      String description) {}

  public record TransferRequest(
      @NotBlank String sourceWalletId,
      @NotBlank String destinationWalletId,
      @NotBlank String amount,
      @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String currency,
      String referenceId,
      String description) {}

  public record ReferenceRequest(String referenceId, String description) {}
}
