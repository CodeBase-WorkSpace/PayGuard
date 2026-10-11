# Collateral service

The domain/application layer models cash collateral locks, valuation history, LTV thresholds, margin calls, liquidation authorization, retention metadata, audit records, and outbox publication. Price and liquidation integrations are ports; the portfolio profile supplies a simulated foreign-currency oracle and execution adapter.

The supplied SQL schema and infrastructure now use the service-owned PostgreSQL database. Concrete repository adapters/entities are still absent, so schema tests do not prove the planned persisted business workflows. Flyway runs separately with a Vault migration identity; runtime obtains scoped DML credentials through the rotating Vault DataSource.

From the repository root with JDK 25 and Docker:

```bash
./mvnw -B -ntp -pl payguard-collateral-service -am test
```

The integration suite applies PostgreSQL migrations and checks exact amounts, identifiers and important constraints. Follow [the implementation guide](../docs/guides/postgresql-vault-migration.md) for TLS infrastructure, provisioning, migration commands, startup and remaining limitations. Original Oracle migrations remain unchanged under `src/main/resources/db/legacy/oracle` for authorized historical migration only. Runtime database passwords do not belong in `.env`.
