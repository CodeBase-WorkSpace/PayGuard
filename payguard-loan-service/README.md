# Loan service

The service owns the `Loan` aggregate, schedules, Actual/365 accrual, repayment waterfall, retention metadata, immutable audit records, outbox publication, and idempotent Kafka consumers. It references opaque borrower and collateral identifiers and requests wallet settlement through an event/port boundary.

Build independently from the repository root with `docker build -f payguard-loan-service/Dockerfile -t payguard/loan-service .`. Run it with `SPRING_DATASOURCE_URL="$LOAN_ORACLE_JDBC_URL"`, `SPRING_DATASOURCE_USERNAME="$LOAN_ORACLE_APP_USER"`, and `SPRING_DATASOURCE_PASSWORD="$LOAN_ORACLE_APP_PASSWORD"` from `.env`; its Flyway schema is applied only to the loan-owned Oracle instance.
