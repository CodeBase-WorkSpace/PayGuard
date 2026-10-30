# PayGuard

**A privacy-focused, secure modular fintech backend for wallet ledgering, loan origination, and digital-collateral risk management.**

[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](#)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Architecture](https://img.shields.io/badge/Architecture-Hexagonal%20%7C%20DDD-blueviolet?style=flat-square)](https://alistair.cockburn.us/hexagonal-architecture/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

[Overview](#overview) •
[Services](#services) •
[Architecture](#architecture) •
[Key Engineering Decisions](#key-engineering-decisions) •
[Getting Started](#getting-started) •
[API Documentation](#api-documentation) •
[Roadmap](#roadmap)

</div>

---

## Overview

PayGuard is a **privacy-focused** and **security-first** backend system designed for a single, coherent financial story: a customer locks digital assets as collateral, borrows against them, and pays the loan back — while the system continuously monitors the collateral's value and protects itself automatically if that value falls too far.

Built on the principle that sensitive systems should run entirely on your own hardware to keep data completely private[cite: 2], PayGuard is designed to be self-hosted, ensuring you retain absolute control over financial records. The project is constructed as a set of **independently deployable microservices**, each owning its own completely isolated database. They communicate only through explicit contracts — never through a shared schema — bringing the risk of data leakage to absolute zero.

It is a deliberate engineering exercise in the domain problems that sit underneath every regulated lending product, executed with the highest security standards:

- **Immutable and privacy-preserving ledgers:** Double-entry bookkeeping and derived, never-stored balances to eliminate data tampering.
- **Secure and idempotent financial operations:** Guaranteed transaction integrity under concurrent access.
- Loan amortization, repayment waterfalls, and delinquency state machines built for strict auditability.
- Loan-to-Value (LTV) risk monitoring, margin calls, and liquidation via protected, automated mechanisms.
- **Strict service-to-service communication boundaries:** REST at the edge for external clients, and secure gRPC where internal latency and security matter.

It is not a payment-card switch or a full core-banking platform. The scope is intentionally narrow so that every part of the architecture can be built, understood, and defended in depth against security vulnerabilities.

---

## Core Capabilities

### Implemented / In Progress

- [ ] Account creation and double-entry ledger posting (`wallet-service`)
- [ ] Hold → capture → release flow for provisional balance reservations
- [ ] Loan application, approval, and amortization-schedule generation (`loan-service`)
- [ ] Repayment waterfall (fees → interest → principal)
- [ ] Collateral locking and Loan-to-Value calculation (`collateral-service`)
- [ ] Margin-call and liquidation risk monitoring
- [x] API Gateway routing (REST-in, gRPC-out to internal services)

### Planned

- [ ] Kafka-based domain events between services (e.g. loan approval → collateral linkage)
- [x] Full CI pipeline (build, test, static analysis) via GitHub Actions
- [ ] Expanded integration and concurrency test coverage

> This README describes the target architecture and is kept in sync with implementation status via the checkboxes above — see [Current Limitations](#current-limitations) for what is intentionally out of scope for now.

---

## Services

| Service | Responsibility | Persistence | Current State |
|---|---|---|---|
| `wallet-service` | Account balances, double-entry ledger, holds/captures | Dedicated PostgreSQL database with jOOQ Open Source configured; SQL adapter pending | In development |
| `loan-service` | Loan lifecycle, amortization schedules, repayments | Dedicated PostgreSQL schema; JPA repository adapter pending | In development |
| `collateral-service` | Collateral locking, price valuation, LTV monitoring, liquidation | Dedicated PostgreSQL schema; JPA repository adapter pending | In development |
| `api-gateway` | Single entry point; REST from clients, gRPC to internal services | — | Planned |

> `wallet-service` deliberately uses jOOQ instead of JPA: a ledger is append-only, and jOOQ gives explicit control over `INSERT`-only SQL with no risk of an accidental `UPDATE` slipping in through dirty-checking. `loan-service` and `collateral-service` manage mutable, CRUD-shaped state, where JPA is a better fit.

---

## Architecture

### System Overview

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

Use JDK **25**, Docker/Compose, Ansible and OpenSSL. The Maven wrapper is included. Database integration tests start real PostgreSQL containers; they do not require an unset opt-in flag.

```bash
./mvnw -B -ntp clean verify
```

Historical Identity verification is recorded in [the publication repair log](docs/tasks/identity-publication-repair.md). It does not validate the new Vault Agent lifecycle; see [current infrastructure validation](docs/infrastructure-validation.md).

For the Compose, Vault Agent, Ansible and Flyway CLI deployment, follow [the infrastructure runbook](docs/infrastructure.md). It gives the exact preparation, initialization, migration and test commands. Generated credentials, certificates and unseal material live under ignored `.local/payguard` with protected permissions. `.env.example` contains only public settings. Never copy secrets into an image or publish that directory.

Runtime Flyway is disabled: apply migrations using the migration identity before starting applications. The `applications` Compose profile is separate from the database/Vault infrastructure. Provider delivery, customer enrollment, external authentication, production certificate issuance, HA/auto-unseal and durable event delivery are not implemented by this migration.

## Existing data and historical decisions

Oracle scripts are preserved byte-for-byte under each owning module's `db/legacy/oracle`. PostgreSQL has a separate migration history and never executes those scripts. No existing volume is deleted or treated as empty. Legacy UUID challenge/customer records require an approved persistent identifier mapping; no identities are regenerated to make an import succeed.

The cutover is an offline write freeze, rehearsed export/import and reconciliation. After PostgreSQL accepts new writes, returning to Oracle requires reverse migration or reconciliation; retaining an old volume alone is insufficient. Historical ADRs retain their original context, with database-related decisions superseded by ADR 0011–0014.

See [the migration guide](docs/guides/postgresql-vault-migration.md), [durable work log](docs/tasks/postgresql-vault-migration.md) and [separate issue drafts](docs/issues/postgresql-vault-follow-up.md) for actual verification evidence and remaining work.
