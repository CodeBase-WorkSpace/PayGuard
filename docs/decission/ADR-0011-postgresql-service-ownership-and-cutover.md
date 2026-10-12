# ADR-0011: PostgreSQL ownership and offline cutover

## Status
Accepted for this implementation; live cutover is not authorized.

## Context
Java 25 / Spring Boot 4.1.1 is the existing platform. Identity has JPA adapters; Loan and Collateral have Oracle schemas but no concrete domain repositories. Wallet declares jOOQ OSS 3.19.15 but has no schema, SQL adapters, generator configuration or generated records. API Gateway has no database. Existing Oracle data and legacy identity mappings are unknown.

## Options considered
Retain Oracle; replace every adapter with jOOQ; or adopt PostgreSQL while preserving actual persistence approaches. Choose the last option. H2 cannot validate the target engine.

## Decision
Pin PostgreSQL 17.11 (supported major until November 2029). Keep independent databases for Identity, Wallet, Loan and Collateral. Identity's four modules share its database. Local rehearsal uses separate containers to isolate credential managers; runtime roles never receive superuser, schema ownership, CREATE or another service's privileges. Use a no-login owner, separate migration login and no-login DML group, with owner default privileges for future migrations.

Normal Flyway locations are `db/migration/postgresql`; unchanged Oracle files live under `db/legacy/oracle`. PostgreSQL V1 installs current types directly; Identity V2 installs issuance serialization and an active-row unique index. Never copy Oracle Flyway history. Runtime starts only after separately authorized migration provisioning. Hibernate validates mappings.

Map real UUID RAW(16) values through a documented canonical byte-order conversion to PostgreSQL uuid; preserve prefixed case-sensitive IDs as varchar with C collation. Decimal precision remains numeric, never floating point; identity counters are integer; BIGINT identity replaces numeric identity; CLOB is text. Instant values use timestamptz(6): microseconds and UTC instants, not original offsets or Java nanoseconds. Oracle DATE is reviewed per column; last_accrual_date is a LocalDate/date. Empty-string/null differences require explicit export normalization for optional fields, never identifiers.

Use TLS verify-full and SCRAM for non-disposable application and Vault connections. Ephemeral Testcontainers alone may use isolated plaintext transport with synthetic credentials.

## Cutover and failure behavior
Choose offline write freeze, verified backup/export, parent-before-child import, row counts, orphan/constraint checks and representative comparisons. Existing UUID challenge/customer references require an approved persistent mapping; refuse invented mappings. Rehearse with synthetic fixtures. No clean, truncate or volume removal on existing data. Before PostgreSQL writes, Oracle remains rollback source; after writes, reverse migration/reconciliation is mandatory.

## Tradeoffs and scope
No Java/Spring upgrade; no new ledger schema invented for Wallet. Missing adapters and generated SQL remain separate work and limit platform-wide claims. Local single-instance containers do not provide production HA, encrypted hosting volumes or operational certification.

## Verification
Run actual Flyway and PostgreSQL round trips, upgrade V1 to V2, numeric/UUID/time/case/foreign-key checks, role denial and Identity races. Live-data migration requires authorized source/mapping.

## References
- https://www.postgresql.org/support/versioning/
- https://www.postgresql.org/docs/17/datatype.html
- https://www.postgresql.org/docs/17/ddl-priv.html
- https://www.jooq.org/download/
