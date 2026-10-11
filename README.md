# PayGuard

PayGuard is a Java 25 / Spring Boot 4.1.1 financial-platform project under development. Domain ownership, explicit financial amounts, stable customer identities and database isolation are design requirements; this checkout is not a production-readiness or compliance claim.

## Current services

| Module | Persistence available in source | Current database integration |
| --- | --- | --- |
| `payguard-identity-service` | JPA challenge/audit adapters; existing-customer OTP issue/verify | PostgreSQL 17.11, Flyway, serialized verification/replacement; Vault database leases and scoped KV secrets |
| `payguard-loan-service` | Domain/application ports and loan SQL schema; concrete repository adapter absent | PostgreSQL schema and real precision/constraint tests; Vault pool |
| `payguard-collateral-service` | Domain/application ports and collateral SQL schema; concrete repository adapter absent | PostgreSQL schema and real valuation/constraint tests; Vault pool |
| `payguard-wallet-service` | Domain/ports and OSS jOOQ dependency; no SQL schema, adapter or code-generation configuration | POSTGRES dialect and Vault pool configured; ledger persistence remains separate work |
| `payguard-api-gateway`, `payguard-proto` | No owned database | Database-free |

Identity contains access, customer, verification and registration modules in one deployment and one owned database. Access and registration workflows remain largely planned. The retired step-up-token endpoint is not restored.

## Ownership and engineering decisions

Each business service has its own PostgreSQL database, runtime group, durable schema owner and independently scoped Vault AppRole. Local rehearsal uses four private PostgreSQL containers. Runtime roles have DML privileges; Flyway runs through a separate migration identity. Applications never receive a database administrator or Vault root token.

Domain rules remain in the domain, use-case coordination in application code, and JDBC/JPA/jOOQ/Vault details in infrastructure. Wallet's intended append-only ledger and hold/capture separation remain design decisions; the absent SQL adapter means their persisted concurrency behavior has not been established. Loan and Collateral continue to use their existing JPA dependency rather than being rewritten to jOOQ. Cross-service access belongs behind explicit ports, with no foreign keys or table reads across service databases.

- [PostgreSQL ownership and cutover ADR](docs/decission/ADR-0011-postgresql-service-ownership-and-cutover.md)
- [Vault secrets, bootstrap and licensing ADR](docs/decission/ADR-0012-vault-service-secrets-and-bootstrap.md)
- [Database credential and pool lifecycle ADR](docs/decission/ADR-0013-database-leases-and-pool-replacement.md)
- [OTP concurrency ADR](docs/decission/ADR-0014-otp-postgresql-concurrency.md)
- [Identity architecture](payguard-identity-service/ARCHITECTURE.md)

## Build and local rehearsal

Use JDK **25**, Docker/Compose, Python 3 and OpenSSL. The Maven wrapper is included. Database integration tests start real PostgreSQL containers; they do not require an unset opt-in flag.

```bash
./mvnw -B -ntp clean verify
```

The full build still exposes existing domain/static-analysis defects described in the implementation guide. Do not bypass those checks to claim a passing build.

For the isolated, persistent TLS PostgreSQL/Vault rehearsal, follow [the implementation guide](docs/guides/postgresql-vault-migration.md). It gives the exact preparation, initialization, migration and test commands. Generated credentials, certificates and unseal material live under ignored `.local/payguard` with protected permissions. `.env.example` contains only public settings. Never copy secrets into an image or publish that directory.

Runtime Flyway is disabled: apply migrations using the migration identity before starting applications. The `applications` Compose profile is separate from the database/Vault infrastructure. Provider delivery, customer enrollment, external authentication, production certificate issuance, HA/auto-unseal and durable event delivery are not implemented by this migration.

## Existing data and historical decisions

Oracle scripts are preserved byte-for-byte under each owning module's `db/legacy/oracle`. PostgreSQL has a separate migration history and never executes those scripts. No existing volume is deleted or treated as empty. Legacy UUID challenge/customer records require an approved persistent identifier mapping; no identities are regenerated to make an import succeed.

The cutover is an offline write freeze, rehearsed export/import and reconciliation. After PostgreSQL accepts new writes, returning to Oracle requires reverse migration or reconciliation; retaining an old volume alone is insufficient. Historical ADRs retain their original context, with database-related decisions superseded by ADR 0011–0014.

See [the migration guide](docs/guides/postgresql-vault-migration.md), [durable work log](docs/tasks/postgresql-vault-migration.md) and [separate issue drafts](docs/issues/postgresql-vault-follow-up.md) for actual verification evidence and remaining work.
