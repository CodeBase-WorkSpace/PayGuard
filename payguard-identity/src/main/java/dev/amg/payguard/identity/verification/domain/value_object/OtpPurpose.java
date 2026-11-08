package dev.amg.payguard.identity.verification.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface OtpPurpose
    permits OtpPurpose.Login,
        OtpPurpose.TransferConfirmation,
        OtpPurpose.LoanDisbursement,
        OtpPurpose.CollateralRelease,
        OtpPurpose.CardIssuance,
        OtpPurpose.ProfileChange {
  OtpPurpose LOGIN = new Login();
  OtpPurpose TRANSFER_CONFIRM = new TransferConfirmation();
  OtpPurpose LOAN_DISBURSE = new LoanDisbursement();
  OtpPurpose COLLATERAL_RELEASE = new CollateralRelease();
  OtpPurpose CARD_ISSUANCE = new CardIssuance();
  OtpPurpose PROFILE_CHANGE = new ProfileChange();

  @JsonValue
  String name();

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static OtpPurpose valueOf(String name) {
    return switch (name) {
      case "LOGIN" -> LOGIN;
      case "TRANSFER_CONFIRM" -> TRANSFER_CONFIRM;
      case "LOAN_DISBURSE" -> LOAN_DISBURSE;
      case "COLLATERAL_RELEASE" -> COLLATERAL_RELEASE;
      case "CARD_ISSUANCE" -> CARD_ISSUANCE;
      case "PROFILE_CHANGE" -> PROFILE_CHANGE;
      default -> throw new IllegalArgumentException("Unknown OtpPurpose: " + name);
    };
  }

  final class Login implements OtpPurpose {
    private Login() {}

    @Override
    public String name() {
      return "LOGIN";
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class TransferConfirmation implements OtpPurpose {
    private TransferConfirmation() {}

    @Override
    public String name() {
      return "TRANSFER_CONFIRM";
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class LoanDisbursement implements OtpPurpose {
    private LoanDisbursement() {}

    @Override
    public String name() {
      return "LOAN_DISBURSE";
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class CollateralRelease implements OtpPurpose {
    private CollateralRelease() {}

    @Override
    public String name() {
      return "COLLATERAL_RELEASE";
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class CardIssuance implements OtpPurpose {
    private CardIssuance() {}

    @Override
    public String name() {
      return "CARD_ISSUANCE";
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class ProfileChange implements OtpPurpose {
    private ProfileChange() {}

    @Override
    public String name() {
      return "PROFILE_CHANGE";
    }

    @Override
    public String toString() {
      return name();
    }
  }
}
