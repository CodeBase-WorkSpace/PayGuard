# PayGuard OTP Service

`payguard-otp-service` is the bounded context for login MFA and sensitive-action
step-up authentication. It is independently deployable, persists challenge
metadata in Oracle, and keeps rate-limit counters and one-time step-up tokens in
Redis.

## API

- `POST /otp/challenges` issues a challenge for an opaque `userId`, purpose,
  channel, destination reference, and optional device fingerprint.
- `POST /otp/challenges/{challengeId}/verify` verifies the six-digit code and
  returns a single-use, purpose-scoped step-up token.
- `POST /otp/step-up-tokens/consume` lets a downstream gateway consumer redeem
  the token once for the exact user and purpose.

SMS, email, and push channels use random six-digit codes. Authenticator-app
challenges use an RFC 6238 TOTP verifier behind `AuthenticatorSecretPort`; the
secret is resolved by a vault/identity adapter and is never stored in the OTP
database. The default mock delivery adapter discards codes without logging them.

## Privacy and security posture

- Only opaque user IDs and destination references are stored; phone numbers and
  email addresses remain in the identity service.
- Random codes are salted and HMAC-SHA256 hashed with a server-side pepper.
- Challenges are single-use, expiry-bound, device-bindable, and limited to five
  failed attempts by default.
- Redis enforces issuance/verification rate limits and stores three-minute,
  single-use step-up tokens.
- Oracle retains challenge/audit facts for 90 days by default; the purge job is
  intentionally externalized so retention can be configured by jurisdiction.
- Audit records contain no OTP code and are indexed by subject and time.

## Local validation

Unit tests run with Maven. The Oracle/Redis smoke test is enabled explicitly:

```bash
RUN_OTP_INTEGRATION_TESTS=true ./mvnw -pl payguard-otp-service -am test
```

Production requires `SPRING_DATASOURCE_*`, `REDIS_HOST`,
`OTP_SECURITY_PEPPER`, and `KAFKA_BOOTSTRAP_SERVERS` environment variables.
