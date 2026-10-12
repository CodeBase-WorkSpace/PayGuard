# PostgreSQL and Vault implementation guide

## Scope and evidence boundary

This guide records the migration originally developed in `amg-postgresql-vault`, published in PR #35, and integrated with the owner's Identity replacement on `amg-complete-identity-service`. No existing-volume deletion or live-data cutover was performed.

Available business services are Identity, Wallet, Loan and Collateral. Gateway and Proto have no owned database. Identity contains four package modules in one deployment, so it keeps one owned database. Source inspection found actual Identity JPA challenge/audit adapters, but only repository ports in Loan/Collateral and no Wallet schema, SQL adapter or jOOQ code-generation setup. Configuring their PostgreSQL/Vault infrastructure does not establish persisted financial workflows that do not yet exist.

Normal runtime/build/service tests on the integration branch select PostgreSQL. Oracle migrations remain historical input, unchanged, in vendor-separated legacy locations. No running Oracle source or approved historical UUID-to-prefixed-ID mapping was supplied; live-data migration remains untested and blocked on those inputs. Absence of a running database was never treated as evidence that data could be discarded.

## Publication scope and dependencies

Migration-only PR #35 deliberately excluded the owner's Identity domain PR #33, OTP retirement, root module switch and unchanged application foundations. PRs #33 and #35 were subsequently merged. The integration branch preserves the owner's retirement commit, registers Identity in the reactor and includes its required application, bootstrap, adapter and test files. Five unused future identifier classes and unrelated local files remain outside the publication.

The historical migration rehearsal results below came from the earlier full local checkout. The current integration was separately verified from a clean committed worktree: full Maven verification on `fd29d35` passed 91 tests and every configured quality gate. The first clean run reproduced two domain regressions; bounded production fixes restored stable identity hashing and rejection before challenge creation, with the existing test assertions retained. See [the publication repair log](../tasks/identity-publication-repair.md) for current publication and CI status.

## Versions and decisions

| Component | Selected/resolved version | Reason |
| --- | --- | --- |
| Java / Spring Boot | 25 / 4.1.1 | Actual parent; no unrelated framework upgrade |
| PostgreSQL | 17.11-bookworm, digest in Compose | Supported PostgreSQL major, reproducible server image |
| Vault Community | 2.1.2, digest in Compose | Self-hosted AppRole, KV v2, database engine, Raft and audit; no Enterprise dependency |
| Flyway / JDBC / Hikari | 12.4.0 / 42.7.13 / 7.0.2 | Actual Boot dependency management |
| Testcontainers / JUnit | 2.0.5 / 6.0.3 | Actual managed dependencies |
| jOOQ | OSS 3.19.15 | Existing Wallet dependency; POSTGRES dialect, no paid distribution |
| Redis / Kafka | 8.0.3-alpine / 4.0.1 | Explicit local tags, no `latest` infrastructure images |

PostgreSQL 17 support is scheduled through November 2029. Vault 2.1.2's actual LICENSE is BUSL 1.1 with an additional noncompeting internal-production use grant and a later MPL 2.0 change license. Community is not described here as OSI open source. The selected features do not need an Enterprise subscription. Recheck the actual license before offering a competing hosted product. [PostgreSQL support](https://www.postgresql.org/support/versioning/), [Vault 2.1.2 LICENSE](https://github.com/hashicorp/vault/blob/v2.1.2/LICENSE), [HashiCorp licensing FAQ](https://www.hashicorp.com/en/license-faq), [jOOQ editions](https://www.jooq.org/download/).

Accepted decisions:

- [ADR 0011: PostgreSQL ownership, types and offline cutover](../decission/ADR-0011-postgresql-service-ownership-and-cutover.md)
- [ADR 0012: Vault classification, AppRole, key handling and license](../decission/ADR-0012-vault-service-secrets-and-bootstrap.md)
- [ADR 0013: Lease/pool replacement and failure policy](../decission/ADR-0013-database-leases-and-pool-replacement.md)
- [ADR 0014: OTP row/scope locking and rejection commits](../decission/ADR-0014-otp-postgresql-concurrency.md)

## Ownership and credential flow

| Service | Database | Durable owner / runtime group | Runtime Vault identity and role | Schema owner |
| --- | --- | --- | --- | --- |
| Identity | `payguard_identity` | `identity_owner` / `identity_runtime` | `local-identity` / `local-identity-runtime` | Verification migrations in Identity |
| Wallet | `payguard_wallet` | `wallet_owner` / `wallet_runtime` | `local-wallet` / `local-wallet-runtime` | No business schema supplied |
| Loan | `payguard_loan` | `loan_owner` / `loan_runtime` | `local-loan` / `local-loan-runtime` | Loan migrations |
| Collateral | `payguard_collateral` | `collateral_owner` / `collateral_runtime` | `local-collateral` / `local-collateral-runtime` | Collateral migrations |

Before: independent Oracle containers and datasource passwords from environment variables. Identity additionally accepted a known development pepper fallback. After: four private PostgreSQL servers with TLS/SCRAM, protected server/bootstrap artifacts, separate migration AppRoles, and short-lived Vault-issued runtime logins. Four servers make each credential manager's CREATEROLE authority local to one service rather than relying on cluster-wide role names to isolate a shared server.

`prepare.py` creates no-login owner/runtime groups, a separate bootstrap migrator and dedicated `vault_manager`. PUBLIC database and public-schema privileges are revoked. The owner creates schema `payguard`; runtime gets CONNECT, schema USAGE, table SELECT/INSERT/UPDATE/DELETE and sequence USAGE/SELECT through owner default privileges. It gets no schema CREATE, TEMP, role administration or migration-history access. Owner-created functions have no public EXECUTE by default. `SchemaMigrator` revokes audit UPDATE/DELETE and Identity scope DELETE after migrations. Runtime DML permissions do not establish a complete append-only financial adapter.

Vault's manager has LOGIN/CREATEROLE and ADMIN membership only in this server's runtime/owner groups, with INHERIT/SET disabled. Applications never receive it. `database/rotate-root/local-<service>` replaces its initial password; initial protected passwords are intentionally stale afterward. Manager rotation differs from runtime lease rotation. Losing the manager connection requires operator repair with separately protected PostgreSQL administrative authority, then an explicit rotate-root and issuance test.

Migration logins inherit the durable owner group and execute `SET ROLE <service>_owner` before DDL, so objects are owned by a no-login role. Revoking an expiring migration login can therefore clean it up without dropping business objects. Runtime logins inherit only their runtime DML group and own no schema objects. PostgreSQL plugin revocation explicitly removes membership and drops the login. [PostgreSQL privileges](https://www.postgresql.org/docs/17/ddl-priv.html), [Vault PostgreSQL engine](https://developer.hashicorp.com/vault/docs/secrets/databases/postgresql).

The application reads its service-only RoleID/SecretID files, verifies Vault's HTTPS certificate, authenticates with AppRole, reads only its database role and installs a validated Hikari pool. Identity can additionally read `secret/data/local/identity/otp-keys` and its authenticator subtree. Wallet cannot obtain Identity/Loan/Collateral credentials or secrets. Runtime policies cannot issue migration credentials, administer Vault or revoke another role's leases.

| Existing material | Classification and resulting source | Rotation / missing behavior |
| --- | --- | --- |
| Four runtime database logins | Dynamic database secrets engine, independently scoped runtime AppRoles | Pool generation replacement before expiry; no static fallback |
| Schema migration logins | Separate database roles and operator/build AppRoles | Expiring login owns no objects; durable owner retains schema |
| Vault database manager passwords | Separate operational credential per PostgreSQL server | `database/rotate-root`; never supplied to applications |
| PostgreSQL bootstrap passwords, Vault shares/operator token, AppRole SecretIDs, CA private key | Ignored mode-0600 operator artifacts for rehearsal | Operator-only provisioning, renewal/recovery and protected custody |
| Identity OTP/device-binding HMAC pepper | KV v2 named key ring at `secret/data/local/identity/otp-keys` | Required at startup; versioned/explicit legacy compatibility, bounded cache |
| Customer authenticator seeds | KV v2 customer path under `local/identity/authenticators` | Missing seed returns empty; denial/outage fails as dependency error before attempt mutation |
| Public customer/challenge IDs and AppRole RoleIDs | Identifiers | Preserve exact values; they are not authentication credentials |
| Delivery-provider/API signing credentials | No implemented credential-consuming provider/authentication adapter found | No fictitious secret path or integration added; delivery defaults remain synthetic/mock |

Runtime and migration policies explicitly permit lookup/renew/revoke of their own token and do not inherit Vault's default policy. Lease renewal/revocation is constrained to the runtime role prefix. The operator policy enumerates its four migration credential paths; Vault ACL glob syntax must not be mistaken for arbitrary middle-of-path wildcard matching.

## Schema and type mapping

| Service / source type | PostgreSQL representation | Preserved meaning and limits |
| --- | --- | --- |
| Identity current challenge/customer IDs | `varchar(26)` / `varchar(255) COLLATE "C"` | Opaque `otp_`/`cus_` values, exact case and bytes; no trimming/lowercasing/UUID conversion |
| Identity audit `RAW(16)` / Java UUID | native `uuid` | UUID audit identity remains UUID; canonical RAW hex conversion uses network byte order, verified by synthetic vector |
| Identity attempts / limits | `integer` | Attempts bounded 0..max; ACTIVE cannot be exhausted; existing domain transitions retained |
| Identity hashes / enum names | bounded `varchar` | TOTP code hash nullable; device binding nullable; RANDOM_NUMERIC needs a nonblank hash |
| Identity creation/expiry/consumption/retention | `timestamptz(6)` | Instant semantics; consumed nullable except USED; expiry and consumption checks enforced |
| Loan account/collateral/event IDs | native `uuid` | Cross-service collateral identifier remains opaque reference with no cross-database foreign key |
| Loan amount / annual rate | `numeric(28,8)` / `numeric(20,12)` | Same decimal precision/scale; no floating point |
| Loan accrual day | `date` | Business LocalDate retained |
| Loan version / generated audit/outbox ID | `bigint` / bigint identity | Exact integer representation; generated IDs remain numeric |
| Collateral IDs / loan reference | native `uuid` | Owned valuation FK retained; no FK into Loan database |
| Collateral amount/value / FX / LTV thresholds | `numeric(28,8)` / `numeric(28,12)` / `numeric(10,8)` | Existing exact scales and currency column retained |
| Loan/Collateral CLOB payload | `text` | Outbox content retained as text, no invented JSON schema |
| Loan/Collateral timestamps | `timestamptz(6)` | Nullable retention/publication stays nullable; no original-offset preservation claim |
| Wallet Money | Existing BigDecimal + Currency | Domain currency fraction digits/HALF_EVEN unchanged; no database mapping exists to migrate |

PostgreSQL stores an instant for timestamptz, not the original offset. Precision is microseconds; arbitrary Java nanoseconds are not preserved. Identity explicitly truncates application timestamps to microseconds, including the time re-evaluated after acquiring a verification lock. The offline converter also explicitly truncates source precision. Review/reconcile any historical sub-microsecond data before approving cutover.

Oracle's empty-string-as-null behavior is not reproduced automatically. Existing domain validation and null fields are reviewed explicitly; PostgreSQL constraints reject important blank hashes. Unquoted names remain lower-case SQL identifiers. SYS_GUID defaults become PostgreSQL UUID generation only for actual UUID audit identity. PL/SQL is preserved historically rather than translated mechanically. There are no Oracle native business queries, jOOQ generated statements or code-generation configuration in the available runtime source.

## Flyway history and OTP concurrency

PostgreSQL Identity V1 creates the current string-ID shape directly. V2 adds persistent customer/purpose scope rows, backfills existing scopes and creates a partial unique ACTIVE index. Loan/Collateral each have PostgreSQL V1. These are new vendor histories: Oracle checksums/history remain separate and are never copied into PostgreSQL as proof of migration.

Normal application Flyway is disabled. Provision the owner/schema, run separate migration tooling, then start the service; Hibernate validates Identity mappings instead of creating tables. Integration tests enable real Flyway before Hibernate validate. `flyway.clean` stays disabled. Duplicate historical ACTIVE challenges cause V2 to fail transactionally; the migration never deletes or guesses which challenge to retain.

Verification uses a PostgreSQL row lock inside a bounded REQUIRES_NEW adapter transaction. The adapter restores current state, applies the callback, flushes and commits. The application throws its business rejection only after that return, so failed attempts, final lock and expiry transitions persist. One concurrent caller gets fresh VERIFIED; the competitor sees USED and gets ALREADY_USED. Failed attempts serialize; successful and failed final allowed attempts each produce a valid terminal transition. Lock/connection failures propagate as persistence/dependency failures and are not reported as verification success or automatically retried.

Issuance upserts and locks an enduring `(user_id,purpose)` scope before invalidating locked ACTIVE rows and inserting a replacement. This also serializes first issuance when no challenge exists. The partial unique index is a final invariant. Other customers/purposes are unaffected. Insert-only `save` prevents merging stale snapshots over current state.

Quota acquisition and Vault-backed TOTP verification occur outside the database lock. The locked mutation rechecks ownership, current state and current time. Audit, delivery and Kafka remain separate side effects; their existing failure/commit gap is documented as separate work. No exactly-once delivery, stored verification response idempotency or database/broker atomicity is claimed.

## Persistent local setup: exact commands

Prerequisites: clean source checkout with JDK 25 selected in JAVA_HOME/PATH, Docker/Compose, Python 3 and OpenSSL. Use a disposable rehearsal host. Existing `.local/payguard` or named volumes may contain data; preparation refuses to overwrite artifacts and no command below deletes volumes.

```bash
./mvnw -B -ntp -pl payguard-identity-service,payguard-wallet-service,payguard-loan-service,payguard-collateral-service -am -DskipTests package dependency:copy-dependencies -DincludeScope=test
python3 scripts/local/prepare.py
docker compose --env-file .env.example up -d --wait postgres-wallet postgres-loan postgres-collateral postgres-identity vault
python3 scripts/local/vault_bootstrap.py
python3 scripts/local/migrate.py identity
python3 scripts/local/migrate.py loan
python3 scripts/local/migrate.py collateral
python3 scripts/local/verify_vault.py
python3 scripts/local/verify_applications.py
```

Skipping tests in the first command builds helper classpaths; it does not claim verification. Run the suites below. Wallet has no schema migration command because no business schema was supplied.

The parent Maven Flyway plugin reads connection settings directly from `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` and `FLYWAY_INIT_SQL` in the invoking process. They are not Maven property references, so ordinary IDE imports and builds do not need migration credentials. An explicitly managed Maven migration must receive a verified TLS URL, a separately issued migration login and `FLYWAY_INIT_SQL` selecting the service's durable owner, for example `SET ROLE identity_owner`. That variable overrides the plugin's default SQL, which refuses database operations when owner-role selection is omitted. The supported local commands above use `migrate.py` and `SchemaMigrator`, which retain their internal `PAYGUARD_MIGRATION_*` variables, derive the owner from the validated service name, apply the post-migration grants and revoke the operator token. A raw Maven invocation does not perform that surrounding workflow. [Flyway Maven environment configuration](https://documentation.red-gate.com/fd/maven-goal-277579365.html).

Preparation creates mode-0600 files under a mode-0700 ignored directory: synthetic random bootstrap passwords, CA/private keys and per-server TLS certificates. Both application-to-database and Vault-to-database URLs use `sslmode=verify-full` with the local CA. The CA/certificates expire after 30 days; this is explicit local rehearsal material, not production certificate lifecycle. TLS remains enabled on persistent Vault/Raft. Testcontainers tests use isolated disposable test passwords/plain local JDBC; they are not the non-disposable design.

`PostgreSqlTls` validates the configured single-host JDBC URL before any connection. It requires an absolute CA path and exactly one `sslmode=verify-full`; duplicate/encoded overrides, URL passwords, userinfo and custom SSL factories/hostname verifiers are refused. Multi-host failover is not implemented by this local topology. Owner default EXECUTE is revoked globally for future functions because a per-schema revoke cannot remove PostgreSQL's global PUBLIC default grant.

Vault startup remains sealed until initialization/unseal. Bootstrap initializes three shares with a two-share threshold, stores them in protected local operator artifacts, unseals and waits for leader readiness. It enables KV v2, the database engine, AppRole and a file audit device, provisions independent service policies, rotates manager passwords, creates a separately scoped orphan operator token and revokes/removes the initial root token. Apps mount only their AppRole directory and CA. Administrative/unseal/operator artifacts are not mounted into applications or copied into Docker images.

RoleIDs are public identifiers; SecretIDs/tokens/unseal shares are secrets. Provisioning obtains a 60-second response-wrapped SecretID and unwraps once into a protected service file; service SecretIDs expire after one hour. Runtime tokens have 20-minute renewable leases with a four-hour maximum. The operator coordinates fresh files atomically before SecretID expiry:

```bash
python3 scripts/local/refresh_approle.py
# Run under a supervised local operator process for a long rehearsal:
python3 scripts/local/refresh_approle.py --watch
```

The watcher renews its operator token and refreshes runtime files every 20 minutes. It stops on operator failure. It is an operator process, not application administration; production needs actual protected supervision/workload identity and alerting. Existing application tokens continue until their valid limits, then reauthenticate using the newest file. If refresh stops and material expires, startup/re-authentication fail; no default credential is accepted. Vault's operator token also has a server-imposed maximum lifetime; recover/re-provision before that boundary.

After a Vault restart, rerun bootstrap to unseal using the protected local shares. If the operator token expired/lost, explicit threshold-key recovery is available:

```bash
python3 scripts/local/vault_bootstrap.py --recover-operator
```

This uses Vault's threshold recovery protocol, creates an independent operator token and revokes the temporary recovery root token. It requires protected unseal shares; no long-lived root token is put in `.env`. Local share co-location is a rehearsal convenience; production needs separate custody and recovery storage.

Ordinary bootstrap is repeatable: it retains an existing database engine configuration and rotated manager password, retains existing OTP key material, refreshes policies/AppRoles and never reinitializes Raft or business data. `--diagnostics` displays only failed bootstrap API paths and HTTP codes, never response bodies or credentials. After operator renewal fails, use explicit recovery; elapsed time is not authorization to regenerate or delete data.

Applications start only after migration and secret provisioning. Set `PAYGUARD_LOCAL_UID`/`PAYGUARD_LOCAL_GID` in a public `.env` to the host owner of protected service files if different from 1000. Keep `PAYGUARD_ENVIRONMENT` aligned with independently provisioned environment policies/paths; the included bootstrap is explicitly local.

```bash
docker compose --env-file .env.example --profile applications up -d --build
```

This is a local start command, not evidence that every financial workflow exists. Provider integration/configuration and missing repository adapters can block application business flows; inspect actual startup/readiness. Identity readiness includes db, Vault authentication and valid OTP keys. No service reports persistence readiness merely because a scheduler is alive.

`verify_applications.py` instead boots each actual packaged JAR on the private network with only its own service AppRole directory and CA. It polls HTTP readiness inside the temporary container, checks secret markers in captured logs and removes only its own application probe containers. It does not mount administrative material, expose a public API, delete volumes or simulate a missing financial adapter. All four packaged services reached readiness UP locally. This proves startup/wiring, not Wallet ledger or Loan/Collateral repository flows.

## Pool, key and failure lifecycle

`LeasedDataSource` acquires a credential lease (default one hour, max four hours), validates a Hikari generation and switches future borrowers at half TTL. Old idle connections are evicted; checked-out old transactions drain for up to ten seconds, also bounded by lease safety deadline. Replacement uses a fresh credential rather than assuming Spring Cloud Vault automatically rotates/reconfigures a DataSource at max_ttl. It retries failed replacement once per second while old credentials remain valid, limits retained generations to eight and rejects borrowers at TTL minus two seconds. Lease timing starts conservatively before the issuing request. Old generations close/revoke after drain/deadline. [Spring Cloud Vault limitation](https://docs.spring.io/spring-cloud-vault/reference/secret-backends.html).

Long transactions beyond drain/deadline can fail. PostgreSQL runtime statement/lock/idle-transaction timeouts are ten seconds. No transaction is replayed automatically: callers need their actual business idempotency/recovery contract. The pool has four maximum connections per generation and briefly overlaps generations. No capacity measurement is claimed.

Revoking a PostgreSQL role stops new logins; existing established sessions can remain valid. Tests demonstrate this. Application-owned generations close after the bounded drain, but emergency external revocation does not imply immediate termination of every session. If forced termination is required, operators need an explicitly authorized database session-termination procedure.

The pool validates CONNECT and schema USAGE on startup and each borrow, so an established session passing `SELECT 1` after role revocation cannot masquerade as a usable runtime connection. Failed borrowing requests asynchronous credential/pool replacement, with a one-second maintenance interval and bounded network/acquisition calls; legitimate work resumes only after validation. It does not retry the caller's transaction. Forced revocation may break in-flight DML even though the session itself survives; detection of every arbitrary table-grant configuration change is outside this check.

Vault outage: existing valid database credentials work only to their safety deadline; new issuance/authentication fails, and readiness is down. Database outage: connection acquisition fails within the pool timeout, no verification succeeds, and recovery allows fresh legitimate work. Invalid required keys, missing provisioning, denied policy and expired authentication have sanitized dependency errors. There is no known password/pepper fallback. Secret values are omitted from errors, pool names and snapshot/key-ring diagnostics.

Identity KV key ring has `current`, `keys` and an optional explicit `legacy`. New hashes are `keyId:salt:digest`; key material cannot change under an existing ID. To rotate, add a new random named key, select it as current and retain the previous key for at least max(random OTP TTL, TOTP TTL) plus ten seconds after its last use. Keep an explicit legacy key until all unversioned stored challenges/device bindings are expired or reconciled. Never guess the old key. Refresh happens every five seconds outside SQL locks; valid cached keys have a bounded five-minute outage lifetime, then fail closed. Removing a needed key early is rejected; do not overwrite pepper material under the same name.

The explicit legacy alias cannot be reassigned to another key by a live refresh: unversioned hashes carry no information to select that different key. Removing the alias is also rejected during the compatibility window, even if the old key material remains in the ring. Removing key material and removing the alias are both retirement operations.

Retirement is an operator-coordinated decision across instances, not a distributed retention registry. Allow the five-minute cached-key outage window plus maximum challenge TTL plus ten seconds after switching the Vault current key, and confirm all issuers have refreshed/stopped using the old key before removal. Local refresh guards reject unsafe removal for already running instances; newly started instances cannot recover key material removed from Vault. Failed refresh sets the OTP key readiness contributor DOWN while still-valid cached keys remain bounded; startup requires valid provisioned material. Preserve legacy key provenance across restart rather than guessing from a hash.

OTP pepper rotation, AppRole token renewal, PostgreSQL login replacement and authenticator seed re-enrollment are separate operations. Seed rotation is not implemented as enrollment/recovery. Vault stores recoverable TOTP seeds in encrypted storage with scoped KV reads. No existing business field needing a new recoverable field-encryption adapter was identified; no Transit/card vault or indiscriminate column encryption was invented. Local TLS and Vault storage protection do not implement PostgreSQL volume/backup encryption; hosting must supply that layer.

## Audit, backup and recovery

Persistent Vault audit output is `/vault/audit/audit.json` on the protected `vault_audit` volume. Default HMAC protection remains enabled. The test runner verifies allowed/denied accesses and hashed credential values without printing raw audit output. Reading audit files belongs to operators; paths and access metadata remain sensitive even when values are protected.

Vault can reject requests if it cannot write to any enabled audit device. This single-device local design therefore requires disk-capacity monitoring, retention/rotation and protected backups before production. No automatic audit retention, HA or auto-unseal was implemented. Do not solve disk failure by disabling audit or raw-value protection. PostgreSQL error-statement/parameter logging is suppressed to avoid CREATE ROLE password leakage.

For a local backup with an installed compatible Vault CLI, supply the operator token via protected input; never print it or put it in command-line arguments. Set only public TLS settings normally:

```bash
export VAULT_ADDR=https://127.0.0.1:8200
export VAULT_CACERT="$PWD/.local/payguard/ca.crt"
umask 077
read -r -s -p 'Operator token: ' VAULT_TOKEN
export VAULT_TOKEN
vault operator raft snapshot save .local/payguard/operator/raft.snapshot
unset VAULT_TOKEN
```

Back up PostgreSQL with separately authorized owner/admin tooling, protect/encrypt exports, retain the approved ID mapping, and store Vault recovery shares separately from encrypted snapshots. Rehearse restore into isolated infrastructure with the original key/certificate custody. Snapshot restore replaces Vault state and is an explicit operator recovery action, not an automatic command in setup. These backup/restore CLI steps were documented, not executed; live hosting/encryption/custody remain production-dependent.

## Existing-data rehearsal and cutover

1. Establish an authorized immutable Oracle export and inventory its vendor migration history, rows, numeric totals, UUID byte order, empty strings and timestamp precision. Export no secrets into ordinary logs. Keep Oracle histories unchanged.
2. Approve a persistent mapping artifact for every legacy customer/challenge identifier. Do not create new customer identities per request. Already-current prefixed IDs pass through exactly; mapping is still a provenance-reviewed artifact.
3. Freeze writes for an offline cutover. Preserve source export, mapping checksum, row counts and representative records. Do not copy Oracle `flyway_schema_history` into PostgreSQL.
4. Provision an empty owned PostgreSQL destination and apply its own Flyway migrations using the separate migration identity.
5. Convert a protected export with `scripts/migration/convert_identity_export.py`. Its JSON input has `challenges` and `audit` arrays named after current database columns. The mapping has `approved_by`, `approval_reference`, `customers` and `challenges` maps keyed by exact source identifiers. Actual audit UUID/RAW hex is preserved as UUID; timestamps require offsets and explicitly truncate to microseconds. Missing mapping, duplicate identity and orphan audit records are refused.
6. Import parents before children with an explicit command. The importer locks/checks an empty destination, inserts in one transaction, relies on constraints/ACTIVE uniqueness, verifies audit subject ownership, row counts and every supplied field, then commits. Populated destinations and reconciliation errors are refused; failures roll back without truncation.

```bash
python3 scripts/migration/convert_identity_export.py \
  .local/payguard/operator/source-export.json \
  .local/payguard/operator/approved-id-mapping.json \
  .local/payguard/operator/converted-identity.json
python3 scripts/local/migrate.py identity \
  --import-identity .local/payguard/operator/converted-identity.json
```

The mapping/export must be supplied by an authorized operator; there is no live exporter credential or invented customer mapping. Conversion outputs are exclusive-create/mode 0600 and include mapping SHA-256 plus counts/provenance. Verify full source/destination counts, orphan queries, case-sensitive identity/reference equality, challenge state/attempts, expiry/consumption/retention and sampled audit records independently before opening traffic.

Loan/Collateral tests rehearse their supplied schemas and representative exact numeric/UUID/date/time/null/idempotency/FK records. Live Oracle export/import and financial balance reconciliation were not executed. Wallet lacks a schema/adapter, so no Wallet ledger migration or platform-wide financial concurrency claim is made. Before a real Loan/Collateral cutover, supply authorized exports, import in owned parent/child order, preserve sequence identities or advance sequences deliberately and reconcile principal/outstanding/accrued/valuation totals and currency grouping. Those live tooling decisions remain blocked on actual source data and missing adapters.

Before PostgreSQL accepts writes, rollback can redirect to the frozen Oracle source after verifying no target-only writes occurred. After it accepts writes, rollback requires reconciling/reverse-migrating target changes and side effects; retaining an Oracle volume alone is not sufficient. Prefer rehearsing recovery forward with backups and correction procedures before choosing a reverse cutover. Do not enable clean, drop volumes, truncate tables or regenerate identities to bypass a failed rehearsal.

## Change ledger

| Repository-relative path | Before → resulting behavior | Reason / evidence |
| --- | --- | --- |
| `pom.xml` | Oracle dependency management removed; PostgreSQL Flyway plugin reads native `FLYWAY_*` variables at execution time, with required owner-role selection | Managed compatible versions; Java/Spring unchanged; parent validation passed; isolated PostgreSQL 17.11 Maven migration applied Loan V1, verified five tables/two sequences owned by `loan_owner`, and rejected omitted owner initialization. The Vault-managed helper remains the complete local migration workflow |
| `payguard-{identity,loan,collateral,wallet}-service/pom.xml` | Oracle JDBC/Flyway/test containers → PostgreSQL and actuator | Actual service persistence approach retained; compilation and real database suites |
| `payguard-{identity,loan,collateral,wallet}-service/src/main/resources/application.yaml` | Oracle/env runtime passwords → PostgreSQL/Vault pool, explicit readiness | No pepper/password fallback; runtime Flyway disabled; separate migration path |
| `payguard-identity-service/src/main/resources/db/migration/postgresql/V1__identity_schema.sql` | New vendor history in final prefixed-ID shape | UUID audit/string identity/timestamp/check constraints |
| `payguard-identity-service/src/main/resources/db/migration/postgresql/V2__serialize_active_challenges.sql` | Scope-lock rows and partial ACTIVE index | First issuance race and populated V1 upgrade/rejection rehearsal |
| `payguard-{loan,collateral}-service/src/main/resources/db/migration/postgresql/V1__*_schema.sql` | Oracle types/defaults → PostgreSQL exact equivalents | Real Flyway and exact numeric/UUID/date/time/constraint tests |
| `payguard-{identity,loan,collateral}-service/src/main/resources/db/legacy/oracle` | Original scripts relocated unchanged | Preserve deployed Oracle histories/guards; never normal Flyway locations |
| `payguard-identity-service/src/main/java/dev/amg/payguard/identity/verification/domain/port/OtpRepository.java` | Separate load/save → locked mutation contract; save inserts only | Cross-process state freshness/explicit commit boundary |
| `payguard-identity-service/src/main/java/dev/amg/payguard/identity/verification/infrastructure/persistence/JpaOTPRepositoryAdapter.java` | Unserialized writes → row/scope locks and ordered replacement | Real concurrent verification/issuance and rollback tests |
| `payguard-identity-service/src/main/java/dev/amg/payguard/identity/verification/infrastructure/persistence/OtpChallengeJpaRepository.java` | Ordinary queries → explicit locked current-row/ACTIVE queries | PostgreSQL transactions |
| `payguard-identity-service/src/main/java/dev/amg/payguard/identity/verification/application/OtpApplicationService.java` | Stale verification save → committed result boundary, remote calls outside locks, microsecond/current locked time | Rejection commits, final attempts, ownership and expiry tests |
| `payguard-identity-service/src/main/java/dev/amg/payguard/identity/verification/domain/model/OtpSnapshot.java` | Nullable lifecycle contradictions → conditional consumed/binding validation and safe copies | Needed to restore real persisted states; no unrelated domain redesign |
| `payguard-identity-service/src/main/java/dev/amg/payguard/identity/verification/infrastructure/security` | Known pepper/unavailable seed → named HMAC ring and scoped Vault adapter | Real seed/rotation tests; enrollment remains absent |
| `payguard-shared/src/main/java/infrastructure/vault/VaultClient.java` | No client → verified TLS AppRole, bounded auth/renew/read calls, sanitized errors | Token renewal/re-authentication and policy/outage tests |
| `payguard-shared/src/main/java/infrastructure/vault/LeasedDataSource.java` | Static pool → validated credential/pool generations and bounded drain | Real short TTL/maxTTL, held transaction and outage tests |
| `payguard-shared/src/main/java/infrastructure/vault/VaultDatabaseAutoConfiguration.java` and `src/main/resources/META-INF/spring` | No shared wiring → infrastructure auto-configuration/readiness | Actual Spring wiring test |
| `payguard-shared/src/main/java/infrastructure/vault/SchemaMigrator.java` | No separate executable → real Flyway as owner role | Actual local Identity/Loan/Collateral migration commands and post-migration privilege restrictions |
| `payguard-shared/src/main/java/infrastructure/vault/IdentityDataImporter.java`, `scripts/migration` | No rehearsal path → approved mapping, empty transactional import/reconciliation | Synthetic Python/real PostgreSQL tests; no live source claim |
| `scripts/local/{common,prepare,vault_bootstrap,refresh_approle,migrate,verify_vault,verify_applications}.py` | Environment passwords/manual assumptions → protected provisioning and actual dependency/application runners | Local TLS initialization, recovery, manager rotation, credential tests and four real readiness probes |
| `payguard-identity-service/.../customer/domain/value_object/PrefixedIdValidator.java`, `package-info.java` | Missing referenced validator → exact existing prefix/22 ASCII character validation and package nullness | Current checkout failed compilation without this helper; existing typed-ID tests pass, no accessor/ID changes |
| `docker-compose.yaml`, `infrastructure/{postgresql,vault}`, `.env.example`, `.dockerignore` | Oracle/dev configuration → private TLS PostgreSQL and persistent non-dev Vault | No existing volume touched; secret directories excluded from images |
| `payguard-{identity,loan,collateral}-service/src/test` | Oracle/gated or datasource-excluding fixtures → actual PostgreSQL/Flyway tests | No opt-in test flag; disposable test profile is explicit |
| `.github/workflows/_database-security.yml`, `pipeline.yml`, `_publish.yml` | No Vault lifecycle CI → independent security suite, four real application probes and SHA-tagged service image builds | Workflow calls real test/migration/runner commands; remote CI execution not claimed |
| `README.md`, `payguard-identity-service/{README,ARCHITECTURE}.md`, `docs/decission/ADR-0011..0014` | Outdated Oracle/unresolved claims → implemented scope and explicit limits | Source-based handover; no compliance/production claim |

## Verification commands and results

Use JDK 25; a default older JDK fails `release version 25 not supported` and is not an application regression.

```bash
./mvnw -B -ntp -pl payguard-identity-service,payguard-loan-service,payguard-collateral-service -am \
  -Dtest=PostgreSqlTlsTest,PostgresqlMigrationRehearsalTest,SanitizedDependencyErrorTest,OtpInfrastructureIntegrationTest,OtpApplicationContractsTest,LoanServiceApplicationTests,CollateralServiceApplicationTests \
  -Dsurefire.failIfNoSpecifiedTests=false clean test
python3 -m unittest discover -s scripts/migration -p 'test_*.py'
./mvnw -B -ntp -pl payguard-identity-service,payguard-wallet-service,payguard-loan-service,payguard-collateral-service -am -DskipTests package dependency:copy-dependencies -DincludeScope=test
python3 scripts/local/verify_vault.py
python3 scripts/local/verify_applications.py
./mvnw -B -ntp verify
```

Do not run clean builds concurrently with the Java Vault runner: it reads shared target classes/dependencies and writes reports there. The runner stops/restarts only the isolated Identity PostgreSQL container, seals/unseals only the local rehearsal Vault and restores its shortened role/key fixtures. It must never point to a production environment.

Baseline full verify failed existing shared import formatting before tests. Baseline Identity diagnostic run executed 53 tests with 4 failures, 25 errors and 8 skips. Required nullable lifecycle fixes made persisted flows testable. An earlier verification executed 76 tests with two domain-contract failures; the current checkout contains owner changes to aggregate identity hashing and pre-creation validation, and those regressions now pass. They are recorded as historical findings, not unresolved defects.

Current committed integration evidence: `./mvnw --batch-mode --no-transfer-progress verify` on `fd29d35` exited 0 in a clean worktree with **91 tests, zero failures/errors/skips** (Identity 74, other modules 17). Spotless, Checkstyle, PMD, CPD and SpotBugs passed. This includes the 14 PostgreSQL Identity infrastructure cases and existing concurrency/domain/application tests. Native Maven Flyway configuration was also verified on disposable PostgreSQL 17.11: Loan V1 installed, all five tables/two sequences were owned by `loan_owner`, and missing owner initialization was rejected. The Vault-managed helper remains the complete local migration workflow.

Historical migration rehearsal evidence from the earlier full working checkout:

- Earlier normal `./mvnw -B -ntp verify`: **95 tests passed, zero failures/errors/skips**. Shared 4, Proto 1, Gateway 2, Wallet 1, Loan 5, Collateral 4, Identity 78. Exit code **1** because Identity PMD reported six existing `AvoidFieldNameMatchingMethodName` findings; no checks were disabled. Log `/tmp/payguard-final-current-verify.log`.
- All modules before Identity passed normal Spotless/Checkstyle/PMD/CPD/SpotBugs. Independent Identity SpotBugs passed with zero bug/error instances; its six typed-ID PMD naming findings remain separate work. Package build with tests skipped succeeded for all four service JARs; it is not counted as test evidence.
- Identity real PostgreSQL suite: 14 passing tests, including competing verification, failed attempts, first issuance/replacement, final boundaries, restored states, ownership, case and rollback. Application contracts: 11 passing tests. Loan/Collateral tests exercise exact schema amounts/UUID/time/null/constraints; absent persisted financial flows remain unproved.
- Synthetic export conversion: 3 Python tests passed, zero skipped.
- Shared TLS parameter guard and secret-error redaction: one passing test each; populated V1/V2/import rehearsal: two passing tests. Expected duplicate ACTIVE upgrade refusal rolls back without deleting records.
- All four actual packaged applications reached HTTP readiness UP using dedicated AppRole files and verified PostgreSQL TLS (`/tmp/payguard-application-readiness.log`), and probe containers were stopped/removed. No financial API-flow claim follows from startup.
- Initial/recovered and ordinary repeat bootstrap, atomic SecretID refresh and all three local migration commands succeeded with retained volumes. Identity V1/V2, Loan V1 and Collateral V1 have their own Flyway histories; migration token revocation completed.
- Previous real Vault run XML contained 15 passing tests, zero skips (~115 seconds). The restored runner first failed 12 shared setup cases because the operator migration path used an invalid middle glob; explicit per-service paths corrected that defect.
- Final `python3 scripts/local/verify_vault.py` completed with exit **0** after legacy-alias hardening: **15 tests passed, zero failures/errors/skips**, approximately 116 seconds, including four runtime DML/default grants, denied DDL/admin/cross-service reads, ten-second statement defaults, real renewal/revocation, continuous pool replacement beyond 45-second max TTL, held transactions, token renewal/re-authentication, manager rotation, seal/unseal, database stop/start, forced revocation recovery, real scoped authenticator lookup and HMAC rotation. This includes refusal to reassign/remove the legacy alias prematurely while retaining old key material. Allowed/denied audit trace and plaintext secret-marker checks passed (`/tmp/payguard-vault-final-alias-tests.log` and `payguard-shared/target/vault-test-reports/TEST-junit-jupiter.xml`).
- Actual Identity Dockerfile build completed with exit **0** (`docker build -f payguard-identity-service/Dockerfile -t payguard/identity-service:rehearsal .`, `/tmp/payguard-identity-docker-build.log`). The image uses pinned Java 25 builder/runtime digests; protected local artifacts are excluded from build context. Tests skipped inside the image build are not counted as executed tests.
- Actual Wallet/Loan/Collateral Dockerfile builds completed with exit **0** (`docker compose --env-file .env.example --profile applications build wallet-service loan-service collateral-service`, `/tmp/payguard-financial-docker-build.log`). Thus all four service Dockerfiles built locally. CI tags output images with the source SHA; Compose derives local image names from its build configuration. No image was pushed or deployed.
- A fresh disposable PostgreSQL initialization using the current `prepare.py` SQL succeeded; runtime CONNECT/USAGE were allowed and schema CREATE/default function EXECUTE denied. The first proof waited on the temporary Unix bootstrap server and failed before initialization finished; a TCP readiness check corrected the fixture. This test used new temporary artifacts/storage with no network and stopped its own container; existing volumes were untouched (`/tmp/payguard-fresh-init-proof.log`).
- A final application probe after Vault/database recovery again reached readiness UP for all four services, scanning both stdout and stderr for secret markers (`/tmp/payguard-application-readiness-final.log`, exit 0). Rehearsal PostgreSQL/Vault containers retain their volumes; temporary application/provisioning probes were stopped/removed.
- After the final legacy-alias guard, normal verification again passed all 95 tests and stopped only at the same six PMD findings. Identity SpotBugs again passed with zero bugs/errors; its actual Docker image was rebuilt successfully from the final Java source. The final Identity-only application probe also reached readiness UP (`python3 scripts/local/verify_applications.py --service identity`, `/tmp/payguard-identity-final-readiness.log`, exit 0).

Surefire reports are under each module's `target/surefire-reports`; the Vault runner writes `payguard-shared/target/vault-test-reports`. Dedicated CI invokes both paths without an unset environment gate. No remote GitHub run, live-data migration, production outage/restore or load/capacity measurement is claimed.

## Remaining Oracle references and limits

PR #35 retained the old OTP module because its retirement was separate owner work. The integration branch removes that module and selects Identity. The remaining Oracle references are historical migrations, documentation and unrelated price-oracle terminology:

- `payguard-{identity,loan,collateral}-service/src/main/resources/db/legacy/oracle/*.sql`: exact historical vendor migrations and the legacy populated-data guard.
- `OtpInfrastructureIntegrationTest.archivedOracleMigrationRetainsPopulatedDataGuard`: reads the legacy guard as text, without an Oracle engine. `PostgresqlMigrationRehearsalTest` mentions the unavailable Oracle source in Javadoc; it executes PostgreSQL only.
- Historical ADRs, `CLAUDE.md`, migration ADRs/work log/this guide: original context, source-data handling and supersession rationale; no normal runtime Oracle configuration.
- `payguard-collateral-service/.../domain/PriceOracle.java` and its callers: price-feed domain terminology, unrelated to Oracle Database.
- Owner-retired `payguard-otp-service` tracked deletions and ignored stale `target/` output are not available runtime modules and are not migration evidence.

Full remaining-reference search is `rg -n -i 'oracle|ojdbc|varchar2|sys_guid|gvenzl|pl/sql' --glob '!**/target/**' --glob '!.local/**'`. Treat matched market-oracle names and URLs to Java documentation separately from JDBC/database references. All original Oracle script bytes are retained; no clean/truncation command is added.

Implemented: available PostgreSQL dependency/config/schema paths, complete Identity module registration, database concurrency, service-scoped Vault client/pool/secrets, persistent local TLS/SCRAM/Raft/audit provisioning, separate migrations and reviewable rehearsal tooling. Current committed Maven verification passes; five unpublished future ID classes remain local owner work. Production-dependent: certificate/SecretID/operator supervision, HA/auto-unseal, encrypted volume/backup hosting, custody, audit retention, capacity and disaster recovery. Not implemented: missing financial repositories, authenticator enrollment/recovery, external auth/Keycloak, durable event/delivery recovery or a new IAM system.

Review [separate GitHub Issue drafts](../issues/postgresql-vault-follow-up.md) for acceptance criteria. Security references describe the PayGuard design, not invented internal company deployments: [OWASP secrets management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html), [cryptographic storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html), [Vault AppRole](https://developer.hashicorp.com/vault/docs/auth/approle), [KV v2](https://developer.hashicorp.com/vault/docs/secrets/kv/kv-v2), [Transit](https://developer.hashicorp.com/vault/docs/secrets/transit), [Stripe customer API-key guidance](https://docs.stripe.com/keys-best-practices).
