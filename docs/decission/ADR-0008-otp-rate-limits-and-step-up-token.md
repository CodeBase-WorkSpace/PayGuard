# ADR-0008: Redis rate limits and opaque one-time step-up tokens

## Status

Accepted

## Decision

Redis counters allow three issuance attempts and twenty verification attempts
per user, purpose, and source IP in a fifteen-minute window. A successful
challenge creates a cryptographically random opaque token in Redis with a
three-minute TTL. Consumption uses Redis get-and-delete and validates the exact
user and purpose.

## Consequences

High-churn state does not burden Oracle. A token cannot be replayed or used for
another purpose, and downstream services can validate it through the OTP
service's gateway contract.
