# ADR-0013: Replace pools before database leases expire

## Status
Accepted for this implementation.

## Context
Spring Cloud Vault documents that reaching database max_ttl does not automatically acquire replacement credentials and reconfigure the DataSource. Merely adding configuration cannot support continuous traffic. Existing BOM manages HikariCP 7.0.2 and PostgreSQL JDBC 42.7.13.

## Options considered
Static runtime passwords; orchestrated process replacement; or in-process pool generation replacement. Choose bounded in-process replacement because this checkout has Docker and no rollout coordinator. Do not assume an absent orchestrator implements rotation.

## Decision
A shared infrastructure DataSource obtains one lease through a service-scoped AppRole, constructs and validates a new Hikari pool, atomically switches future borrowers, evicts old idle connections and drains checked-out old connections before revocation. Acquire replacement at half lease lifetime, well before max TTL; renewal is supported/tested but not relied upon to extend credentials indefinitely. Failed replacement keeps only already valid credentials, retries with a bounded interval and rejects new connections at the safety deadline. No database credentials or lease material appear in pool names, errors or metrics labels.

Bound connection acquisition, SQL statement duration and idle transaction duration. Keep the old generation until transactions return or the bounded drain/deadline expires. At the deadline close the generation; callers may fail and must retry according to their business idempotency contract. Never replay an in-flight financial transaction automatically.

Use no-login runtime groups with DML grants and separate migration ownership. Dynamic roles own no objects. Revoke membership and drop the expiring login after draining. Revoking PostgreSQL login does not guarantee that established sessions immediately stop; selected graceful revocation permits existing work until pool drain/closure. Forced external revocation has a documented session-latency limit rather than a universal immediate-disconnection claim.

Validate database CONNECT and schema USAGE when installing a generation and on each borrow. A revoked role can retain an established session that passes SELECT 1 but lacks application rights. Failed borrowing requests asynchronous replacement on the one-second maintenance cycle; it never replays a caller transaction. Arbitrary changes to individual table grants still require configuration/permission checks. Runtime login defaults bound statement, lock and idle-transaction time to ten seconds.

Vault tokens renew independently; reauthentication uses a still-valid provisioned SecretID. Never grant runtime policies lease-administration privileges over another service. Lease renew/revoke policy is constrained to its own backend/role prefix.

## Failure behavior
Fail startup when authentication, credential issuance or validation fails. Readiness probes must borrow and validate through the active DataSource; a running scheduler alone is insufficient. Temporary Vault outages permit bounded use until credential deadline; prolonged Vault/database outage denies readiness and new persistence work. Recovery installs a tested pool generation. Administrative rotate-root rotates only the manager connection credential and requires its own recovery procedure.

## Scope and tradeoffs
Hikari pools overlap briefly and consume bounded additional connections. Long transactions exceeding drain limits can fail. This is infrastructure outside domain models, not automatic transaction retry or exactly-once processing.

## Verification and references
Use short TTL/max TTL with real Vault/PostgreSQL/Hikari, held transactions, renew, repeated replacement, revoked logins, connection deadline, outage/recovery and manager credential rotation.
- https://docs.spring.io/spring-cloud-vault/reference/secret-backends.html
- https://developer.hashicorp.com/vault/docs/secrets/databases/postgresql
- https://developer.hashicorp.com/vault/docs/secrets/databases
- https://github.com/brettwooldridge/HikariCP
