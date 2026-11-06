package dev.amg.payguard.identity.verification.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface OtpChannel permits OtpChannel.Sms, OtpChannel.Email, OtpChannel.Push {
  OtpChannel SMS = new Sms();
  OtpChannel EMAIL = new Email();
  OtpChannel PUSH_NOTIFICATION = new Push();

  @JsonValue
  String name();

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static OtpChannel valueOf(String name) {
    return switch (name) {
      case "SMS" -> SMS;
      case "EMAIL" -> EMAIL;
      case "PUSH" -> PUSH_NOTIFICATION;
      default -> throw new IllegalArgumentException("Unknown OtpChannel: " + name);
    };
  }

  final class Sms implements OtpChannel {
    private Sms() {}

    @Override
    public String name() {
      return "SMS";
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class Email implements OtpChannel {
    private Email() {}

    @Override
    public String name() {
      return "EMAIL";
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class Push implements OtpChannel {
    private Push() {}

    @Override
    public String name() {
      return "PUSH";
    }

    @Override
    public String toString() {
      return name();
    }
  }
}
