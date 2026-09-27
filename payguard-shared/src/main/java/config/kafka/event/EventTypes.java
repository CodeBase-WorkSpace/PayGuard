package config.kafka.event;

public final class EventTypes {
  public static final String LOAN_APPROVED = "LOAN_APPROVED";
  public static final String LOAN_DISBURSED = "LOAN_DISBURSED";
  public static final String REPAYMENT_APPLIED = "REPAYMENT_APPLIED";
  public static final String LOAN_PAID_OFF = "LOAN_PAID_OFF";
  public static final String LOAN_DEFAULTED = "LOAN_DEFAULTED";
  public static final String LEDGER_TRANSACTION_POSTED = "LEDGER_TRANSACTION_POSTED";
  public static final String MARGIN_CALL_TRIGGERED = "MARGIN_CALL_TRIGGERED";
  public static final String MARGIN_CALL_RESOLVED = "MARGIN_CALL_RESOLVED";
  public static final String LIQUIDATION_TRIGGERED = "LIQUIDATION_TRIGGERED";
  public static final String COLLATERAL_LOCKED = "collateral.locked.v1";
  public static final String COLLATERAL_VALUATION_UPDATED = "collateral.valuation-updated.v1";
  public static final String COLLATERAL_MARGIN_CALL = "collateral.margin-call.v1";
  public static final String COLLATERAL_LIQUIDATED = "collateral.liquidated.v1";
  public static final String COLLATERAL_RELEASED = "collateral.released.v1";
  public static final String LOAN_ORIGINATED = "loan.originated.v1";
  public static final String LOAN_DISBURSED_V1 = "loan.disbursed.v1";
  public static final String LOAN_INSTALLMENT_DUE = "loan.installment-due.v1";
  public static final String LOAN_REPAID = "loan.repaid.v1";
  public static final String LOAN_CLOSED = "loan.closed.v1";

  private EventTypes() {}
}
