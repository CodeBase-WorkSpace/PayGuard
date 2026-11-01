# PayGuard 🛡️

[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](#)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://openjdk.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-JVM-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-yellow?style=flat-square)](LICENSE)

PayGuard is a modular, self-hostable backend for collateral valuation and Loan-to-Value (LTV) monitoring. Built as
independent services on the JVM.

> *Know your LTV before the market tells you.*

Status: **pre-alpha**. APIs, event contracts and database schemas may change before `1.0`.

Current checkout: identity service (OTP issue and verify), loan and collateral domain ports with PostgreSQL schemas,
wallet domain model, API gateway routing, and shared protobuf contracts.
The HTTP API, valuation pipeline and risk engine shown below describe the target interface and are not implemented yet.
See [Build and local rehearsal](#build-and-local-rehearsal) for commands that work with this checkout.

PayGuard serves lenders, factorers, BNPL providers and banks. A tenant registers collateral (invoices, inventory,
equipment, real estate, commodities, securities, crypto and tokenized assets), attaches it to loans, and PayGuard keeps
the LTV current, raises margin calls, and triggers liquidation when thresholds are crossed.

First clone the repository and build it:

```bash
git clone https://github.com/<your-org>/payguard.git
cd payguard
./mvnw -B -ntp clean verify
```

Then start the infrastructure (PostgreSQL and Vault) and the applications:

```bash
docker compose --profile applications up
```

- Starts one private PostgreSQL database per business service
- Starts Vault with a scoped AppRole per service
- Runs Flyway migrations with a separate migration identity before applications start

Talk to it over REST (target API):

```http
POST /v1/loans
Authorization: Bearer <api-key>

{
  "borrower_id": "b_123",
  "principal": 100000,
  "currency": "USD",
  "interest_rate": 0.08,
  "term_months": 12,
  "asset_ids": ["a_456"],
  "ruleset_id": "default"
}
```

```http
GET /v1/loans/{id}/ltv

{ "ltv_current": 0.62, "status": "healthy", "buffer": 0.18, "components": [ ... ] }
```

## Start using PayGuard

Register collateral, request a valuation, then read the LTV:

```http
POST /v1/assets                       { type, external_id, borrower_id, currency, metadata, documents }
POST /v1/assets/{id}/valuations       { trigger, requested_by }
GET  /v1/loans/{id}/ltv
PUT  /v1/loans/{id}/thresholds        { ltv_initial_max, ltv_maintenance, ltv_liquidation }
```

Subscribe to events instead of polling:

```http
POST /v1/webhooks   { url, events: ["loan.margin_call", "loan.liquidation"], secret, active }
```

Events include `asset.created`, `valuation.updated`, `valuation.failed`, `asset.stale_price`, `risk_rule.violated`,
`loan.margin_call` and `loan.liquidation`.

## Principles

#### Isolated

Each business service owns its database, runtime group, schema owner and Vault AppRole. Services talk only through
explicit contracts: REST at the edge, gRPC between services, domain events for state changes. There are no foreign keys
and no table reads across service databases.

#### Auditable

The ledger is double-entry and append-only. Balances are derived, never stored. Every state change writes an
immutable audit record with actor, action and entity. Financial operations are idempotent under concurrent access.

#### Explicit

Domain rules live in the domain, use-case coordination in the application layer, and JDBC, JPA, jOOQ and Vault details
in infrastructure (hexagonal architecture with DDD). Thresholds, haircuts and risk rules are data you configure per
tenant, with documented defaults.

#### Principled

PayGuard does not try to be a full core-banking platform or a payment-card switch. The scope is narrow so each part can
be built, understood and defended in depth.

Example limitations:

- No provider delivery, customer enrollment or external authentication yet.
- No HA or auto-unseal for Vault yet.
- No durable event delivery yet. Kafka events are planned.
- Valuation quality depends on the external price providers you connect.

## Architecture

```text
Customers (Lender / Factorer / BNPL / Bank)
        │ HTTPS + Webhook
        ▼
API Gateway → Rate Limiter → Auth
        │ gRPC
        ▼
┌─────────────────────────────────────────────┐
│                Core Services                │
│  Identity    │ Asset Registry │ Valuation   │
│  Wallet      │ Risk Engine    │ Alert       │
│  Loan        │ Collateral/LTV │ Webhook     │
│  Audit Log   │ Billing        │ Reporting   │
└─────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────┐
│                  Data Layer                 │
│  PostgreSQL (per service) │ Kafka (events)  │
│  Vault (secrets)          │ Object storage  │
└─────────────────────────────────────────────┘
        │
        ▼
External data providers (Chainlink, Pyth, CoinGecko,
Refinitiv, Zillow, Dun & Bradstreet, ...)
```

### Valuation pipeline

Trigger → source fan-out (2 s timeout, retries) → normalizer → outlier filter → weighted aggregator →
confidence scorer → haircut engine → persister → event emitter.

```text
haircut = min(0.95, base + 0.5 × σ30d + 100k / volume + FX + concentration + staleness)
```

Confidence at or above 0.75 is high, 0.45 to 0.75 is medium, below 0.45 is low and raises an alert.

### Risk rules

- **LTV thresholds:** initial max 0.70, maintenance 0.80 (margin call), liquidation 0.90 (after a 24 hour grace period).
  Configurable per asset class.
- **Concentration limits:** per asset, borrower, sector, country, currency, asset class and more.
- **Currency mismatch:** extra haircut for cross-currency collateral.
- **Stale prices:** per asset class, from a freeze with a critical alert to a daily haircut increase.
- **Circuit breakers:** asset-class, asset, provider and system kill switch.
- **AML and KYC:** tiered limits and sanctions screening.
- **SLA escalation:** acknowledge and resolve deadlines per severity.

### Supported collateral

| Class         | Examples                                      | Refresh                  |
|---------------|-----------------------------------------------|--------------------------|
| Physical      | Invoice, inventory, equipment, real estate    | Daily to monthly         |
| Markets       | Commodities, securities                       | 15 minutes to 60 seconds |
| Digital       | Crypto spot, DeFi collateral, LP tokens, LSTs | 5 to 60 seconds          |
| Other digital | NFTs, tokenized real-world assets             | 15 minutes to hourly     |

## Services

Each service is an independently deployable module.

| Artifact                      | Purpose                                                               |
|-------------------------------|-----------------------------------------------------------------------|
| `payguard-identity-service`   | Access, customer, verification and registration. OTP issue and verify |
| `payguard-wallet-service`     | Account balances, double-entry ledger, holds and captures             |
| `payguard-loan-service`       | Loan lifecycle, amortization schedules, repayment waterfall           |
| `payguard-collateral-service` | Collateral locking, LTV calculation, margin calls, liquidation        |
| `payguard-api-gateway`        | Single entry point. REST from clients, gRPC to internal services      |
| `payguard-proto`              | Shared protobuf contracts                                             |
| `payguard-valuation-service`  | Price fan-out, aggregation, haircuts, confidence scoring (planned)    |
| `payguard-risk-service`       | Risk rules engine and concentration checks (planned)                  |
| `payguard-alert-service`      | Alerts, escalation and webhook delivery (planned)                     |
| `payguard-audit-service`      | Immutable audit log (planned)                                         |
| `payguard-billing-service`    | Usage metering and invoicing (planned)                                |
| `payguard-reporting-service`  | KPI dashboards and exports (planned)                                  |

### Current state

| Module                                   | Persistence available in source                                                      | Current database integration                                                                                   |
|------------------------------------------|--------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `payguard-identity-service`              | JPA challenge and audit adapters; existing-customer OTP issue and verify             | PostgreSQL 17.11, Flyway, serialized verification and replacement; Vault database leases and scoped KV secrets |
| `payguard-loan-service`                  | Domain and application ports, loan SQL schema; repository adapter absent             | PostgreSQL schema and real precision and constraint tests; Vault pool                                          |
| `payguard-collateral-service`            | Domain and application ports, collateral SQL schema; repository adapter absent       | PostgreSQL schema and real valuation and constraint tests; Vault pool                                          |
| `payguard-wallet-service`                | Domain, ports and OSS jOOQ dependency; no SQL schema, adapter or code generation yet | PostgreSQL dialect and Vault pool configured; ledger persistence is separate work                              |
| `payguard-api-gateway`, `payguard-proto` | No owned database                                                                    | Database-free                                                                                                  |

> `wallet-service` uses jOOQ instead of JPA on purpose: a ledger is append-only, and jOOQ gives explicit control over
> `INSERT`-only SQL with no risk of an accidental `UPDATE` through dirty-checking. `loan-service` and
> `collateral-service` manage mutable, CRUD-shaped state, where JPA is a better fit.

## Requirements

PayGuard runs on the JVM. Use JDK **25**, Docker and Compose, Ansible and OpenSSL. The Maven wrapper is included.
Database integration tests start real PostgreSQL containers.

## Build and local rehearsal

```bash
./mvnw -B -ntp clean verify
```

For the Compose, Vault Agent, Ansible and Flyway CLI deployment,
follow [the infrastructure runbook](docs/infrastructure.md).
It gives the exact preparation, initialization, migration and test commands.

- Generated credentials, certificates and unseal material live under ignored `.local/payguard` with protected
  permissions.
- `.env.example` contains only public settings. Never copy secrets into an image or publish that directory.
- Runtime Flyway is disabled. Apply migrations with the migration identity before starting applications.
- The `applications` Compose profile is separate from the database and Vault infrastructure.
- Runtime roles have DML privileges only. Applications never receive a database administrator or Vault root token.

## Documentation

- [Infrastructure runbook](docs/infrastructure.md)
  and [current infrastructure validation](docs/infrastructure-validation.md)
- [Identity architecture](payguard-identity/ARCHITECTURE.md)
- [PostgreSQL ownership and cutover ADR](docs/decission/ADR-0011-postgresql-service-ownership-and-cutover.md)
- [Vault secrets, bootstrap and licensing ADR](docs/decission/ADR-0012-vault-service-secrets-and-bootstrap.md)
- [Database credential and pool lifecycle ADR](docs/decission/ADR-0013-database-leases-and-pool-replacement.md)
- [OTP concurrency ADR](docs/decission/ADR-0014-otp-postgresql-concurrency.md)
- [Migration guide](docs/guides/postgresql-vault-migration.md), [work log](docs/tasks/postgresql-vault-migration.md)
  and [follow-up issue drafts](docs/issues/postgresql-vault-follow-up.md)

## Existing data and historical decisions

PostgreSQL migrations live under each owning module's `db/migration/postgresql`. No existing volume is deleted or treated as empty. Legacy UUID
records need an approved persistent identifier mapping; no identities are regenerated to make an import succeed.

The cutover is an offline write freeze, a rehearsed export and import, and reconciliation. After PostgreSQL accepts new
writes, returning to a previous database requires reverse migration or reconciliation. Historical ADRs keep their original context,
with database decisions superseded by ADR 0011 to 0014.

## Reporting Issues / Support

Please use the [issue tracker](../../issues) for feature requests and bugs. For questions, open a
[discussion](../../discussions).

## Reporting Security Vulnerabilities

If you find a security vulnerability in PayGuard, please do not open a public issue. Report it through the repository's
private security advisory feature. See [SECURITY.md](SECURITY.md).

## Inspirations

The domain problems under every regulated lending product: double-entry ledgers, margin lending and collateral
management. The module and decision-record discipline of Spring, Kafka and OkHttp.

## Contributing

Please see [the contribution guide](CONTRIBUTING.md) and the [Code of conduct](CODE_OF_CONDUCT.md) before contributing.
Written code rules are in [CODE_RULES.md](CODE_RULES.md).

## License

PayGuard is distributed under the terms of the MIT License. See [LICENSE](LICENSE).
