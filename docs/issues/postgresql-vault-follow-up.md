# Separate GitHub Issue drafts

These are reviewable drafts, not issues posted to GitHub. They describe existing work outside the database/secrets migration scope.

## Keep OTP identity hashing stable across state changes

**Current status:** Resolved in committed Identity integration `fd29d35`. The clean publication build reproduced the regression before the fix; full verification then passed 91 tests, including the existing stable-hash and collection-membership cases. Do not open this as an unresolved issue without a new failing reproduction.

**Original problem:** `OTP.equals`/`hashCode` included mutable snapshot state. `OTPTest.aggregateHashCodeRemainsStableAfterStateTransition` failed when consumption changed state; a stored hash collection key could become unreachable.

**Acceptance criteria:** Decide aggregate identity semantics explicitly; ensure equality/hash-code consistency and stable collection membership before/after attempts, consumption, lock and expiry. Preserve ValueObject structural comparison and typed ID semantics. Run the existing failing regression and collection membership tests.

## Decide verification timestamps before challenge creation

**Current status:** Resolved in committed Identity integration `fd29d35`. Both pre-creation rejection regressions passed the clean 91-test verification, including wrong-code input without state mutation.

**Original problem:** `OTPTest.verificationBeforeCreationIsRejectedWithoutMutation` expected `IllegalArgumentException`; validation threw `OtpConsumedTimeOutsideValidityPeriodException`. This was a domain/API rejection contract decision, separate from PostgreSQL microsecond precision.

**Acceptance criteria:** Define which public inputs can precede creation and the exact rejection type/result. Rejection must not mutate attempts/status/consumption. Add matching application/REST boundary evidence and resolve the existing test without weakening its invariant.

## Implement missing financial persistence adapters

**Problem:** Wallet has no SQL schema/adapter/code-generation configuration; Loan and Collateral have schemas and repository ports but no concrete persistence adapter/entity. PostgreSQL schema tests cannot establish persisted financial workflows.

**Acceptance criteria:** Implement owning adapters under a separately approved scope. Use OSS jOOQ for Wallet, retain approved domain ownership, explicit transactions, exact amounts/currency, operation idempotency and cross-process competing posting/hold/repayment/valuation tests. No shared tables, cross-service foreign keys or runtime DDL privileges. Generate jOOQ sources reproducibly only once a real schema exists.

## Make Redis quota increment and initial expiry atomic

**Problem:** Existing INCR and first-use EXPIRE are separate commands. A crash between them can leave a permanent quota key.

**Acceptance criteria:** Define an atomic bounded operation and outage policy. Verify concurrent limits, first-use TTL, interrupted requests and recovery against real Redis. Keep per-challenge attempt limits separate and do not repeat quota acquisition when retrying database work.

## Recover audit, broker publication and delivery after committed challenge state

**Problem:** Challenge transactions commit before synchronous audit/Kafka/delivery calls. Failure can leave committed state without its expected side effect; no durable recovery mechanism is implemented.

**Acceptance criteria:** Define durable event/audit/delivery intent, consumer deduplication, ordering and retry ownership. Test failures before/after commit, duplicate deliveries and restart recovery. Protect any recoverable delivery code, restrict access and purge it on a bounded schedule. Do not claim exactly-once publication.

## Define authenticator enrollment, seed recovery and authorization

**Problem:** Vault lookup now reads provisioned customer-scoped seeds, but no approved enrollment/recovery or trusted caller/tenant authorization boundary exists. Synthetic seeds only demonstrate adapter behavior.

**Acceptance criteria:** Approve provider-owned MFA versus PayGuard-owned authenticator use cases, authenticated enrollment/recovery, seed access policy, retention and seed rotation/re-enrollment behavior. Do not introduce an unauthenticated seed endpoint or resurrect step-up tokens. Keep missing enrollment separate from dependency failure.

## Establish production secret and infrastructure operations

**Problem:** Local rehearsal is single-node Raft with manually held unseal shares, a local CA, host-level operator refresh and private Docker databases. It does not implement production HA, managed auto-unseal, disk encryption or backup hosting.

**Acceptance criteria:** Select the real hosting/workload identity and operator custody boundaries, certificate renewal, audit retention/capacity alerting, encrypted backup/restore rehearsal, least-privilege operator provisioning, SecretID refresh supervision and resource limits. Exercise disaster recovery and key retention before claiming production readiness.

## Resolve existing typed-identifier PMD naming findings

**Current status:** The published OtpId finding is resolved by renaming its private field while preserving value(). The other five classes are unused local owner work and remain outside this publication. Normal committed PMD/CPD/SpotBugs and full verification pass; no analysis rule was disabled.

**Problem:** Identity's existing AccountId, MembershipId, OtpAuditId, OtpId, RegistrationId and TenantId each use a `value` field and `value()` accessor. PMD flags these six `AvoidFieldNameMatchingMethodName` findings. The migration preserves their actual accessor/API contracts.

**Acceptance criteria:** Decide a repository-wide convention or a justified precise analysis configuration for these value objects. Preserve serialized prefixed values and public accessors; do not silence broader checks or rename CustomerId.getValue incidentally. Run normal PMD/CPD/SpotBugs and full verify while retaining the passing domain regressions.
