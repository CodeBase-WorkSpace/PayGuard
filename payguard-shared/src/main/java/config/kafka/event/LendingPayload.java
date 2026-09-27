package config.kafka.event;

import java.math.BigDecimal;

public record LendingPayload(
    String loanId,
    String collateralId,
    String subjectToken,
    String status,
    BigDecimal exposure,
    BigDecimal collateralValue,
    BigDecimal ltv,
    String currency,
    String reason,
    String policyVersion)
    implements EventPayload {}
