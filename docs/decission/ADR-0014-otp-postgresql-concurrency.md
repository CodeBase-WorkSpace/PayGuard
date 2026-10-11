# ADR-0014: Serialize OTP persistence transitions in PostgreSQL

## Status
Accepted for this implementation.

## Context
Separate verification load/save transactions permit duplicate VERIFIED results and lost failed-attempt increments. Replacement locks on existing challenges cannot serialize the first issuance. Throwing unchecked business rejections inside a transaction would discard intended failed-attempt/expiry transitions.

## Options considered
JVM locks; optimistic updates with bounded application retry; or PostgreSQL row locks. Choose row locks without automatic business retry. JVM locks do not coordinate independent processes; optimistic retry would complicate quotas and remote TOTP calls.

## Decision
Verification resolves ownership and acquires quota once, performs any Vault-backed TOTP lookup outside database locks, then invokes an adapter transaction that locks the challenge row, rechecks ownership/current state, applies one domain transition and flushes it. Return the result after transaction commit; only then throw OtpVerificationException for business rejection. Competing successful callers observe USED and receive ALREADY_USED, never a new VERIFIED result. Failed callers serialize increments and terminal state; no stale state is copied into the locked row.

Issuance upserts a persistent (user_id,purpose) scope row, locks that row, invalidates matching ACTIVE rows, flushes invalidations and inserts the replacement atomically. A partial unique index is a final active-row invariant. No existing row is required for locking. Customer/purpose scopes remain independent. Retain scope rows; cleanup is separate retention work.

Public save is an insert operation; updates must use the locked mutation contract. This prevents detached stale overwrite. Locks never cover delivery, Vault calls or Kafka publication. The current audit and event calls remain separate commits; durable publication/delivery is separate work and no exactly-once claim is made.

## Required correctness fixes
Restore nullable consumedAt/deviceBindingHash copying, enforce USED iff consumedAt present, and permit null numeric hash for TOTP only. Redact hashes from diagnostics. These fixes restore existing declared contracts required for persistence flows; do not redesign authentication or domain ownership.

## Failure behavior and tradeoffs
Lock/query deadlines fail a dependency request; there is no automatic quota replay. Mutation exceptions roll back; replacement insertion failure restores previous ACTIVE state. Expected business rejection commits its domain transition before transport rejection. Ownership is checked again under lock. Persistent scope rows cost storage and require a later retention policy.

## Verification and references
Independent PostgreSQL transactions/connections compete through barriers: same challenge success/failure, final boundaries, first issuance/replacement, unrelated scopes, expiry, ownership, case and historical-state restoration.
- https://www.postgresql.org/docs/17/transaction-iso.html
- https://www.postgresql.org/docs/17/explicit-locking.html
- https://www.postgresql.org/docs/17/indexes-partial.html
