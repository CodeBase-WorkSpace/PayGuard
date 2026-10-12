# PayGuard Identity Service

Identity contains access, customer, verification and registration modules. Verification implements existing-customer OTP challenges with PostgreSQL JPA persistence, audit records, Redis quotas and Vault-backed secrets. See [ARCHITECTURE.md](ARCHITECTURE.md) for module responsibilities and implementation limits.

## Existing API

- `POST /identity/challenges` issues a challenge for a case-sensitive `cus_...` customer in the existing `userId` field.
- `POST /identity/challenges/{challengeId}/verify` verifies the code and returns `challengeId`, `purpose` and `verifiedAt`. This result is not an authorization credential. The retired step-up-token endpoint remains absent.

Random codes use salted, versioned HMAC-SHA256 hashes. Vault KV v2 supplies the named key ring; an explicitly named legacy key is required for old `salt:digest` records. Rotation retains old keys throughout the configured OTP compatibility window. Device-binding hashes use the same versioned contract.

Authenticator codes use the existing RFC 6238 verifier and a customer-scoped Vault seed adapter. A missing seed returns absence; denied access or unavailable Vault fails as a dependency error before consuming a code-check attempt. This adapter does not implement enrollment or recovery. Mock delivery discards codes without logging them; actual SMS/email/push providers remain absent and fail explicitly when selected.

## Persistence and concurrency

PostgreSQL stores prefixed challenge/customer strings unchanged, UUID audit IDs and microsecond-precision instants. `withLockedChallenge` restores the latest row under a database lock and commits the transition before a business rejection is thrown. Competing callers get one fresh `VERIFIED`, followed by `ALREADY_USED`; failed attempts serialize and cannot overwrite one another.

Issuance locks a durable customer/purpose scope, including first issuance, and a partial unique index permits one ACTIVE row per scope. Other customers and purposes remain independent. Remote Vault, delivery, quota, audit and Kafka operations do not belong to the challenge transaction. Audit/event/delivery failures can still follow committed state; durable delivery is separate work.

Runtime credentials come from a service-scoped Vault AppRole and a rotating pool. Flyway runs separately with migration privileges; Hibernate validates the migrated mapping. Oracle V1/V2 histories remain unchanged under `db/legacy/oracle`. Populated legacy UUID records require an approved mapping before migration.

## Verification and operations

From the repository root, with JDK 25 and Docker:

```bash
./mvnw -B -ntp -pl payguard-identity-service -am test
```

PostgreSQL/Redis integration tests run normally. The dedicated persistent TLS Vault suite has an explicit local/CI runner, with no permanently unset environment flag. See [the implementation guide](../docs/guides/postgresql-vault-migration.md) for provisioning, migration, credentials, rotation, outages and test results.

Remaining limits include trusted caller/tenant authorization, provider delivery, seed enrollment, Redis increment/expiry atomicity, durable audit/event publication and six existing typed-ID PMD naming findings. Current domain regression tests pass. Retention remains at least three calendar years after expiry; no purge job is introduced.
