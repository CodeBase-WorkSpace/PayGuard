# ADR-0009: Optional device/session binding for OTP challenges

## Status

Accepted

## Decision

Callers may provide a device/session fingerprint. The OTP service stores only a
peppered hash and requires the same fingerprint during verification. Binding is
optional for compatibility with login flows that do not yet expose a stable
device identifier.

## Consequences

A stolen code cannot be used from a different bound device. Fingerprints remain
opaque and are not logged; callers must avoid putting raw device identifiers in
the request logs.
