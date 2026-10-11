# Loan service

The domain/application layer models the `Loan` aggregate, schedules, Actual/365 accrual, repayment waterfall, retention metadata, immutable audit records, outbox publication, and idempotent Kafka consumers. It references opaque borrower and collateral identifiers and requests wallet settlement through an event/port boundary.

The supplied SQL schema and infrastructure now use the service-owned PostgreSQL database. Concrete repository adapters/entities are still absent, so schema tests do not prove the planned persisted business workflows. Flyway runs separately with a Vault migration identity; runtime obtains scoped DML credentials through the rotating Vault DataSource.

From the repository root with JDK 25 and Docker:

```bash
./mvnw -B -ntp -pl payguard-loan-service -am test
```

The integration suite applies PostgreSQL migrations and checks exact amounts, identifiers and important constraints. Follow [the implementation guide](../docs/guides/postgresql-vault-migration.md) for TLS infrastructure, provisioning, migration commands, startup and remaining limitations. Original Oracle migrations remain unchanged under `src/main/resources/db/legacy/oracle` for authorized historical migration only. Runtime database passwords do not belong in `.env`.
