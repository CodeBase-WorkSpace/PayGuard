# ADR-0012: Vault service secrets and bootstrap

## Status
Accepted for this implementation.

## Context
Database passwords currently arrive through environment variables. Identity has a known pepper fallback and no production authenticator-secret source. Provider delivery adapters are placeholders without provider credentials. Public customer/challenge IDs are identifiers, not credentials.

## Options considered
Environment-only static secrets; paid managed/Enterprise Vault; or self-hosted Community Vault. Choose Community Vault 2.1.2 using database secrets, KV v2, AppRole, response wrapping and file audit. No namespaces, replication, rootless static-role Enterprise capability or paid jOOQ distribution is required.

## Decision
Use independently provisioned environment/service policies and AppRoles. Runtime reads only its dynamic database role and existing application secret paths. Identity reads a versioned HMAC key ring and customer-keyed synthetic/provisioned authenticator seeds. Missing seed returns Optional.empty; denied/unavailable Vault raises a sanitized dependency error before an attempt is recorded. No unauthenticated enrollment API is added.

Operators provision response-wrapped short-lived SecretIDs into ignored mode-0600 files and deliver role IDs separately. Bootstrap root material stays in protected operator artifacts, never application configuration. Application tokens renew; expiring SecretIDs require operator reprovisioning for later reauthentication. Restarts use the protected provisioned identity. Do not describe a long-lived root token in .env as bootstrap.

Persistent local rehearsal uses TLS with CA/hostname verification, integrated Raft storage, manual initialization/unseal and a file audit device retaining default HMAC protection. No dev mode is the deployment design. Backups include Raft snapshots, protected keys and restore procedures; no HA/auto-unseal is claimed. Audit readers are operators; disk capacity and rotation must be monitored because audit failure can stop requests.

Database-management credentials are independently scoped to a dedicated PostgreSQL server per service and rotated by Vault's rotate-root endpoint after provisioning. Runtime never reads manager or migration credentials. Separate migration policies remain operator/build identities.

## Key handling
HMAC uses current key ID plus retained named keys. New hashes include key ID; legacy salt:digest is accepted only with explicitly named legacy key. Retire a key only after every valid bound challenge has expired; changing KV alone is not compatibility. TOTP seed rotation requires separately provisioned recovery/enrollment. Vault token and database lease rotation do not rotate peppers or seeds.

KV stores recoverable TOTP seeds under encrypted Vault storage; Transit field encryption is not introduced where no existing justified field-encryption use case exists. TLS, Vault storage encryption and hosting volume/backup encryption have different scopes.

## Licensing
The selected upstream LICENSE is BUSL 1.1 with an additional use grant for non-competing production use, and MPL 2.0 after the change date. PayGuard uses Vault internally for its financial application; it does not offer a competing hosted Vault service. Community is source-available, not described as OSI open source. License review is required if the product is repackaged into a competing secrets offering. No Enterprise subscription/license key is needed for chosen capabilities.

## Failure behavior and tradeoffs
Required missing keys/credentials fail startup. Dependency errors are sanitized; no known password/pepper fallback. Manual unseal/bootstrap adds operational responsibility. Production hosting, backup encryption, independent key custody and HA remain deployment decisions.

## Verification and references
Prove allowed/denied paths, AppRole/restart, real KV-backed lookup, secret-marker diagnostics, outage semantics and persistent TLS/audit behavior.
- https://github.com/hashicorp/vault/blob/v2.1.2/LICENSE
- https://www.hashicorp.com/en/license-faq
- https://developer.hashicorp.com/vault/docs/updates/release-notes
- https://developer.hashicorp.com/vault/docs/auth/approle
- https://developer.hashicorp.com/vault/docs/secrets/kv/kv-v2
- https://developer.hashicorp.com/vault/docs/secrets/transit
- https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html
