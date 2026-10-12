# PostgreSQL and Vault implementation work log

Status: scoped implementation and local verification complete. Normal full verify is blocked by six existing Identity PMD naming findings; live-data and missing financial-adapter work remain outside the proven scope. See the guide for production-dependent limitations. Current source is authoritative.

## Authorization and preservation

- Explicit implementation request covers PostgreSQL, Vault, required correctness fixes, real dependency tests, infrastructure, ADRs and documentation.
- Branch `amg-postgresql-vault` was created from local main on 2026-10-11. During implementation no commit/push/PR was authorized. On 2026-10-12 the owner authorized committing/pushing only migration work on `amg-postgres-vault` and creating a PR to main; unrelated owner changes must remain excluded. No deployment, external Issue or live cutover is authorized.
- Preserve substantial owner changes, including retirement of `payguard-otp-service`, untracked Identity, domain validation/identity changes and other dirty files. No old Oracle volume/data was deleted or reset.
- Read full `CLAUDE.md`; no additional applicable `AGENTS.md` found. Explicit implementation overrides its mentor-only default.
- Live Oracle data and approved historical UUID-to-prefixed-ID mapping remain unavailable. Stopped containers never established that existing data was disposable.
- Protected `.local/payguard` files must never be printed, committed, uploaded or copied into images. Mode-0600 files, mode-0700 directories, gitignore and dockerignore remain in place.

## Completed checklist

- [x] Inspect source, parent/module builds, adapters, schemas, configuration, CI and existing ADRs.
- [x] Preserve owner changes and create appropriate temporary branch.
- [x] Record baseline, official selected-version documentation and actual Vault license.
- [x] Migrate available normal build/runtime/tests to PostgreSQL while retaining each persistence approach.
- [x] Preserve historical Oracle scripts in vendor-separated legacy paths.
- [x] Isolate four databases and runtime/migration/manager roles with TLS/SCRAM.
- [x] Implement cross-process Identity verification and first-issuance/replacement serialization, explicit rejection commits.
- [x] Implement scoped AppRole/KV/database client, actual leased pools, bounded credential replacement and failures.
- [x] Implement versioned/legacy HMAC compatibility and customer-scoped authenticator lookup.
- [x] Persistent TLS/Raft/audit rehearsal, bootstrap, threshold recovery, operator refresh and separate migrations.
- [x] Real PostgreSQL migrations/round trips/concurrency and real Vault privileges/lifecycle/outage/key tests.
- [x] Synthetic offline mapping/import/reconciliation with refusal of populated destinations and missing mappings.
- [x] Boot all four actual packaged service JARs with only their own AppRole and CA; HTTP readiness UP.
- [x] Dedicated mandatory CI job, updated README/architecture, ADRs 0011–0014, English guide and Issue drafts.
- [x] All four actual Dockerfile builds succeeded; no images pushed/deployed.

## Source-based service matrix

| Service | Actual owned source | Previous credentials | Result and evidence boundary |
| --- | --- | --- | --- |
| Identity | `payguard-identity-service/src/main/java/dev/amg/payguard/identity/verification/infrastructure/persistence`; actual JPA challenges/audit, Oracle V1/V2 | datasource environment and known pepper fallback | PostgreSQL final V1/scope V2, real adapter transactions/races, Vault pool/keys/seeds; one database for four package modules |
| Loan | `payguard-loan-service/src/main/resources/db/legacy/oracle/V1__loan_schema.sql`; repository port, no concrete entity/adapter | datasource environment | PostgreSQL exact schema/Flyway/SQL fixtures and Vault pool; absent persisted business flows not invented |
| Collateral | `payguard-collateral-service/src/main/resources/db/legacy/oracle/V1__collateral_schema.sql`; repository port, no concrete entity/adapter | datasource environment | PostgreSQL exact valuation schema/Flyway/fixtures and Vault pool; absent persisted business flows not invented |
| Wallet | `payguard-wallet-service/pom.xml` OSS jOOQ dependency/domain/ports; no schema/adapter/codegen/generated source | datasource environment | PostgreSQL driver/POSTGRES dialect and Vault pool; no fabricated ledger schema or platform-wide financial concurrency claim |
| Gateway / Proto | database-free modules | no database credentials | Remain database-free |

## Decisions and versions

- Java 25, Boot 4.1.1, managed Flyway 12.4.0/JDBC 42.7.13/Hikari 7.0.2/Testcontainers 2.0.5/JUnit 6.0.3; existing jOOQ OSS 3.19.15. No Java/Spring upgrade.
- PostgreSQL 17.11-bookworm digest `sha256:3645570cccdfa447589da9f57dd740faa29b30938e861289a5574b6ca6b03826`.
- Vault Community 2.1.2 digest `sha256:c2f666266f383d2cf424d86b8bb8ce7d065562173ffec2b476d762943608bb55`.
- Actual Vault license BUSL 1.1 with additional noncompeting internal-production grant/future MPL 2.0 change; not described as OSI open source. No Enterprise capabilities required.
- Four separate private PostgreSQL servers scope manager CREATEROLE authority per service. Persistent non-dev Vault is single-node Raft/TLS/manual unseal/file audit; no HA/auto-unseal or encrypted hosting-volume claim.
- Separate migration AppRoles issue expiring owner-member logins; SET ROLE durable no-login owner keeps objects revocable. Runtime never receives DDL/admin/root material; runtime Flyway disabled, Hibernate validate.
- Row lock/REQUIRES_NEW committed-result verification; persistent customer/purpose scope locking plus ACTIVE unique index handles first issuance. Remote calls/quota outside locks; no automatic transaction retry or exactly-once side-effect claim.
- Named HMAC key material stays outside source; operator retention includes cached-issuer outage window and OTP lifetime. Missing seed differs from denied/unavailable dependency.
- ADRs: `docs/decission/ADR-0011-postgresql-service-ownership-and-cutover.md`, `ADR-0012-vault-service-secrets-and-bootstrap.md`, `ADR-0013-database-leases-and-pool-replacement.md`, `ADR-0014-otp-postgresql-concurrency.md`.

## Current verification: actual commands and results

1. `JAVA_HOME=/home/amirgm/.jdks/corretto-25.0.4.1 ./mvnw -B -ntp verify`: **95 tests passed, 0 failures/errors/skips**. Shared 4, Proto 1, Gateway 2, Wallet 1, Loan 5, Collateral 4, Identity 78. Exit **1** at Identity PMD: six existing typed-ID field/accessor naming findings. Checks were not disabled. `/tmp/payguard-final-current-verify.log`; all module Surefire XML inspected.
2. Identity real PostgreSQL 14 tests passed; application contracts 11 passed. Includes deterministic independent transaction verification/attempt/first-issuance/replacement races, final boundaries, ownership/case/expiry/restoration, rollback and dependency failure before mutation.
3. Shared real populated V1/V2/import rehearsal 2, strict TLS URL guard 1 and secret-error redaction 1 passed. Expected duplicate ACTIVE upgrade failure rolls back, malformed/orphan imports roll back and populated destinations refuse import.
4. `python3 -m unittest discover -s scripts/migration -p 'test_*.py'`: **3 passed**; approved exact IDs, UUID RAW hex byte order, microsecond truncation, missing mapping/orphan/duplicate/provenance refusal.
5. `python3 scripts/local/verify_vault.py`: exit **0**, **15 passed, 0 failures/errors/skips**, 116 seconds, after final legacy-alias hardening. Real four-service DML/default grants, denied DDL/admin/cross-DB/Vault paths, renew/revoke, existing-session limit, continuous pool replacement past maxTTL, held transaction, forced credential revocation/replacement, token renewal/re-auth/restart, manager rotation, sealed Vault and database stop/start recovery, real TOTP seed and HMAC version/legacy/retirement compatibility. Also rejects premature legacy alias reassignment/removal with old material retained. Audit allowed/denied trace and plaintext secret-marker checks passed. `/tmp/payguard-vault-final-alias-tests.log`; `payguard-shared/target/vault-test-reports/TEST-junit-jupiter.xml` inspected.
6. `python3 scripts/local/verify_applications.py`: all four actual packaged service JARs reached HTTP readiness UP on private network with only dedicated service AppRole directory/CA. Probe containers stopped/removed. Final stdout+stderr secret-marker rerun also passed, exit 0: `/tmp/payguard-application-readiness-final.log`. After final legacy-alias guard, Identity-only probe also passed: `/tmp/payguard-identity-final-readiness.log`.
7. Initial/threshold recovery and ordinary repeat bootstrap succeeded; `refresh_approle.py` renewed operator and replaced SecretIDs atomically. All three separate local migration commands succeeded and revoked their migration tokens. Original volumes retained.
8. Four-module package/dependency build succeeded with tests skipped; `/tmp/payguard-final-package.log`. Not counted as executed tests.
9. All modules before Identity passed normal formatting/Checkstyle/PMD/CPD/SpotBugs. Identity independent SpotBugs passed (0 bugs/errors). Standalone current PMD confirms precisely six existing findings. Final shared/Identity Spotless+Checkstyle passed. No broad analysis suppression added.
10. `docker build -f payguard-identity-service/Dockerfile -t payguard/identity-service:rehearsal .`: exit 0, actual image built using pinned Java25 digests; `/tmp/payguard-identity-docker-build.log`. `docker compose --env-file .env.example --profile applications build wallet-service loan-service collateral-service`: exit 0, all three actual financial service images built; `/tmp/payguard-financial-docker-build.log`. No images pushed/deployed.
11. Compose/security workflow YAML parsed. Remaining Oracle source search found only historical scripts/guard/rehearsal documentation and market `PriceOracle` terminology. Original Loan/Collateral Oracle script bytes match HEAD; no local secret artifact tracked. Diff whitespace check passed.

## Historical findings and continuation repairs

- Baseline full verify stopped on existing shared import formatting before tests. Baseline Identity diagnostic recorded 53 tests, 4 failures, 25 errors, 8 skips. Earlier final run recorded 76 tests, 2 domain failures, no skips. These are historical observations, not current test results.
- Current owner domain changes made stable aggregate hashing and pre-creation validation regressions pass. Preserve those changes; historical Issue drafts are marked resolved, not active blockers.
- At this continuation, previously implemented Python scripts were absent, OtpRepository's locked method was missing, and typed IDs referred to a missing PrefixedIdValidator. Restored actual runnable scripts/contract and supplied exact-format helper based on existing tests. No missing service/shared architecture fabricated.
- Restored operator policy initially lacked audit-list sudo and used a middle wildcard for migration credential paths. Repeat bootstrap and real tests exposed these; explicit policy paths/self-token permissions fixed them. First restored Vault run had 12 setup errors; final rerun all 15 passed.
- Runtime role creation explicitly sets 10-second statement/lock/idle transaction limits; real suite checks statement timeout. Function PUBLIC default EXECUTE is revoked globally, not via ineffective per-schema revoke.
- A new disposable PostgreSQL initialization proof initially hit the temporary bootstrap server before initialization finished; corrected health probe waits for TCP. Final proof passed, exit 0: fresh initialization/CONNECT/USAGE allowed, CREATE/default function EXECUTE denied. `/tmp/payguard-fresh-init-proof.log`. No existing data involved; only the new temporary container stopped/removed.
- Do not run clean builds concurrently with Vault runner; no runner currently active. Secret files remain protected and their contents must not enter tool output.

## Documentation and remaining boundaries

- Guide: `docs/guides/postgresql-vault-migration.md`; change ledger, safe reproduction commands, mappings, cutover/reconciliation, rollback boundaries, permissions, pool/key/outage behavior and observed evidence.
- Issue drafts: `docs/issues/postgresql-vault-follow-up.md`; six typed-ID PMD findings, missing financial adapters, Redis quota atomicity, durable audit/event/delivery recovery, authenticated seed enrollment and production operations. No external Issues posted.
- Live Oracle export/mapping/reconciliation and financial persisted adapter/concurrency flows remain unavailable/unimplemented, not green by inference.
- Production certificate/SecretID/operator supervision, HA/auto-unseal, independent key custody, audit retention/capacity, encrypted volumes/backups and restore exercise remain hosting/operational work.
- Remote GitHub CI has not been run. Alternate Maven Flyway plugin command path and backup/restore CLI are documented, not claimed as executed. Actual Java Flyway helper/migrations are proven.

## Handover boundary

All Docker builds/fresh provisioning/application probes passed. Final review found that changing/removing the legacy alias while retaining old key material bypassed the existing key-retirement guard. Added explicit alias validation and real KV regression coverage. Normal verify again executed all 95 tests successfully and failed only on the same six existing PMD findings; all 15 Vault tests passed, Identity SpotBugs passed, the final image rebuilt and final Identity readiness reached UP. No active build/test runner remains. Persistent local PostgreSQL/Vault containers retain their volumes. No commit/push/deployment performed or authorized. Further work requires the documented PMD decision, missing financial adapters, authorized live data/mappings and production operational decisions; do not infer production readiness from the local result.

## Migration-only publication

An isolated worktree on `amg-postgres-vault` preserves the original checkout/index. The publication excludes the owner's 32-file Identity domain PR #33, existing shared/Wallet nullness changes, CLAUDE.md, OTP module retirement, event-schema and Git-hook edits, and the owner root-module rename. Only migration files are selected. Earlier 95-test and 15-test evidence belongs to the full implementation checkout; a clean migration-only branch still needs the separately owned Identity integration and cannot honestly claim that validation. Commit/push/PR results are reported to the owner separately.
