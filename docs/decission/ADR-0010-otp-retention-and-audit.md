# ADR-0010: Minimized OTP retention and immutable audit facts

## Status

Accepted

## Decision

Challenge metadata and audit facts are retained for 90 days by default, with a
jurisdiction-configurable `retention_until` field. OTP codes and delivery
destinations are never persisted. Audit rows are append-only and indexed by
opaque subject and time to support GDPR accountability and breach scoping.

## Consequences

The service supports fraud investigation without retaining short-lived MFA
secrets longer than necessary. A scheduled purge may delete rows after
`retention_until`; financial records in other bounded contexts are unaffected.
