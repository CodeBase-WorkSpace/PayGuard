# ADR-0004: Daily simple Actual/365 interest

Loan servicing accrues simple interest on outstanding principal for elapsed calendar days using Actual/365 and allocates payments to accrued interest before principal. It avoids compounding interest-on-interest, is deterministic across retries, and keeps the portfolio demonstration explainable. All intermediate calculations use `BigDecimal`; settlement values use currency minor units and HALF_EVEN rounding.
