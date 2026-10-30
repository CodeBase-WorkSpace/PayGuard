# Infrastructure operations

## Ownership

Docker Compose manages process dependencies, persistent-volume identities and networks.
Ansible owns preparation, provisioning, import, backups, credential distribution and recovery.
Vault stores the existing Raft/KV state and issues separate runtime and migration PostgreSQL
credentials for Identity, Wallet, Loan and Collateral. Vault Agent owns AppRole authentication,
token renewal, database-lease renewal and credential delivery. Flyway CLI owns schema migration.
There is no application-startup migration and no Maven migration plugin.

Runtime Agents supervise the service JARs. They wait for their environment templates before
starting Java, renew leases, and send SIGTERM when credentials change. Spring gracefully drains
for 20 seconds; Agent allows 30 seconds before forcing a stop. Run multiple application instances
behind a load balancer if credential replacement must not interrupt availability. The Java Vault
client only reads the Agent sink token for application KV lookups; it never authenticates,
renews, revokes or creates database credentials.

## Requirements

Use a Linux host with a local Unix-socket Docker daemon, Docker Compose >= 2.24.4,
Python 3.12, OpenSSL, coreutils, util-linux, and JDK 25 for application builds. The Docker socket
makes the operations runner an administrative component; never expose it to an application or
untrusted CI workload. Application images contain a JRE and Vault Agent, not Ansible or Docker.

The checked-in image digests are fixed. Python/Ansible/PostgreSQL collection versions are fixed.
OS packages installed by `apk` and platform-specific wheel bytes are not a completely hermetic lock;
produce and scan an image SBOM and promote a built operations-image digest through deployment.
Do not substitute mutable tags during promotion. HashiCorp Vault is subject to its vendor license;
review it against your distribution/service model. The chosen Flyway image is the open-source
edition; no paid secrets resolver is used.

Install the host tools for the preparation/adoption ceremony:

```bash
python3 -m venv .local/ansible
.local/ansible/bin/pip install -r infrastructure/operations/requirements.txt
.local/ansible/bin/ansible-galaxy collection install -r infrastructure/operations/requirements.yml
```

Keep an existing `.env` and `.local/payguard` intact. Compare `.env.example` with your deployment
configuration; it contains names and paths only. Export the deployment environment before invoking
host Ansible or Compose. For a Bash session at the repository root:

```bash
export PAYGUARD_REPO_ROOT="$(pwd -P)"
export PAYGUARD_LOCAL_UID="$(id -u)"
export PAYGUARD_LOCAL_GID="$(id -g)"
export PAYGUARD_DOCKER_GID="$(stat -c %g /var/run/docker.sock)"
export COMPOSE_PROJECT_NAME=payguard
export PAYGUARD_RUNTIME_ROOT=/dev/shm/payguard
mkdir -p -m 700 "$PAYGUARD_RUNTIME_ROOT"
export COMPOSE_FILE=docker-compose.yaml:infrastructure/compose.operations.yaml
```

Use a separate private tmpfs directory for every deployment. Swap must be disabled or protected
with encryption appropriate for secret material. The controller checks that the handoff directory
is tmpfs, owned by the operator, and mode 0700. Do not set DOCKER_HOST or a remote Docker context.

Build the runner once:

```bash
docker compose --profile operations build operations
```

In the commands below, `ops PLAYBOOK` means the following native Compose invocation; substitute
the actual playbook and extra variables rather than installing a wrapper:

```bash
docker compose run --rm --no-deps operations infrastructure/operations/PLAYBOOK.yml
```

## Adopt existing PostgreSQL 18 and Vault state

All seven persistent volumes are external and explicitly named. Normal startup fails if one is
missing, rather than creating an empty replacement. Preserve the existing Compose project and
volume names in `.env.example` or set their exact `PAYGUARD_*_VOLUME` overrides. Do not run
`allocate.yml`, `prepare.yml`, `docker compose down -v`, `flyway clean`, `repair`, or an implicit
baseline against an existing target.

Inspect each volume's existing `PG_VERSION` location read-only before changing any container.
The PostgreSQL 18 image's standard PGDATA is `/var/lib/postgresql/18/docker`, with its named
volume mounted at `/var/lib/postgresql`. If your old container mounted a volume containing a
cluster directly at `/var/lib/postgresql/data`, that same volume will expose `PG_VERSION` at
`/var/lib/postgresql/PG_VERSION` under the new mount. In that case set
`PAYGUARD_PGDATA=/var/lib/postgresql`; do not initialize or move its files. If the four databases
have different layouts, use a reviewed Compose override with a per-service PGDATA. The entrypoint
refuses a version/layout mismatch. A PostgreSQL 17 cluster requires a separate, rehearsed major
upgrade; changing the image is not an upgrade plan.

Take verified backups before cutover, quiescing writers for a consistent cross-service checkpoint.
Preserve current Vault recovery material outside this source tree. The archive contains no live
Docker volumes and cannot prove a live backup or restoration. Record the exact local daemon and
volume identities once:

```bash
.local/ansible/bin/ansible-playbook -i localhost, -c local infrastructure/operations/adopt.yml
```

This only inspects and records existing volume names/daemon ID in ignored `target.yml`. On repeat,
it refuses identity drift. Stateful playbooks compare against that target before mutation.
The database reconciliation preserves existing manager passwords, including Vault-rotated ones.
It creates a manager password only when that manager is absent. Existing schema objects must
already have their stable `<service>_owner`; unexpected ownership fails without reassignment.

Start the four databases and the existing Vault volume. Vault startup does not initialize or
unseal it. If sealed, run the explicit unseal ceremony with two separately protected custodian
key files supplied through an Ansible variable file, or with the existing local rehearsal shares
only when `local_rehearsal=true`. Never pass shares or tokens on the command line.

```bash
docker compose up -d --wait postgres-identity postgres-wallet postgres-loan postgres-collateral
docker compose up -d vault
# custodian-input.yml contains only protected key-file paths; keep it outside source control.
docker compose run --rm --no-deps operations infrastructure/operations/vault-lifecycle.yml \
  -e operation=unseal -e @/protected/custodian-input.yml
docker compose run --rm --no-deps operations infrastructure/operations/site.yml
docker compose up -d agent-operator
```

The site playbook preserves existing mounts, audit configuration, rotated connection passwords
and OTP KV versions. It adds missing service-scoped roles/policies and Redis authentication.
Existing database connection settings must have verified TLS. If an adopted mount/connection or
OTP key is missing, reconciliation fails rather than silently replacing that state. Keep existing
historical SQL and Flyway checksums unchanged. An incompatible history needs a separate reviewed
forward migration; baseline/repair is deliberately not automatic.

The replacement import retains the committed `identity_challenge`, `identity_audit_log`, and
`identity_challenge_scope` tables. No old application data is
converted or rewritten during adoption.

## Fresh isolated environment

Choose a unique Compose project, unique names for all seven volumes and a separate artifact
checkout/runtime directory. Copy the variable names from `.env.example` and assign test-specific
values. A project name alone does not change the explicit default volume names.

```bash
.local/ansible/bin/ansible-playbook -i localhost, -c local infrastructure/operations/prepare.yml -e fresh_target=true
.local/ansible/bin/ansible-playbook -i localhost, -c local infrastructure/operations/allocate.yml -e fresh_target=true
docker compose up -d --wait postgres-identity postgres-wallet postgres-loan postgres-collateral
docker compose up -d vault
docker compose run --rm --no-deps operations infrastructure/operations/vault-lifecycle.yml \
  -e operation=initialize -e fresh_target=true -e local_rehearsal=true
docker compose run --rm --no-deps operations infrastructure/operations/site.yml -e fresh_target=true
docker compose up -d agent-operator
```

`prepare.yml` refuses an existing artifact root, including a partial earlier run. It generates
certificates, bootstrap passwords and protected files only. It produces no secret-bearing SQL
or Docker environment credentials. `allocate.yml` refuses if any requested volume already exists.
These are explicit fresh-only operations; normal reconciliation and migration are repeatable.

The local ceremony stores threshold shares together for disposable fixtures only. Production
initialization instead requires `recovery_pgp_keys` (three custodian public keys) and
`root_token_pgp_key`, supplied through a protected variable file. Encrypted shares and the encrypted
initial root token are saved; custodians must unseal Vault separately, decrypt the root token into
`operator/bootstrap-root` (0600), then run `operation=recover-operator`. That playbook creates the
operator token and revokes the temporary root in an `always` block. The normal startup path never
runs these ceremonies. Use organization-issued certificates and separate recovery custody for
production; the preparation CA is a local rehearsal convenience.

## Independent migrations

Build the committed service modules with JDK 25, then migrate each database explicitly.
This branch retains the existing module names and application persistence implementations:

```bash
./mvnw -B -ntp -pl payguard-identity-service,payguard-wallet-service,payguard-loan-service,payguard-collateral-service -am package
for service in identity wallet loan collateral; do
  docker compose run --rm --no-deps operations infrastructure/operations/migrate.yml -e service="$service"
done
docker compose --profile applications up -d --build
```

Each attempt acquires a per-service lock and a unique private tmpfs handoff directory. An
`exit_after_auth` Vault Agent renders JSON-escaped TOML containing one service's expiring login.
The controller admits only a verified target, protected files, and at least 840 seconds of
remaining token/lease budget. Flyway has a 600-second external deadline plus a 10-second kill
grace, database statement/lock timeouts, and bounded schema-history lock retries.

`afterConnect.sql` selects the stable owner on every connection. Flyway 13 removed `initSql`;
that retired setting is not used. `beforeMigrate` removes runtime table defaults before Flyway
creates its metadata table. Success/error callbacks grant application-table DML, keep history
private, preserve append-only audit restrictions and protect Identity scope deletion. Functions
have no public EXECUTE default. Migration logins have no inherited owner privileges; they must
explicitly select the owner role. Runtime credentials cannot select the owner role or run DDL.

The controller verifies ownership/history privileges, stops only its own Agent/Flyway containers,
and revokes the attempt token and associated leases. It deletes credential files and releases
the service lock only after confirmed revocation. No raw Flyway output is sent to logs. Protected
receipts distinguish `cleanup_verified` and `cleanup_pending`. A nonzero result is a failure,
including a migration that committed but whose cleanup could not be proved.

Wallet currently has domain classes without a persistence adapter. Its independent V1 migration
is an explicit `SELECT 1` marker; this does not invent a financial ledger schema or imply that
Wallet persistence is implemented. Identity, Loan and Collateral SQL files retain their exact
supplied bytes; `infrastructure/migrations.sha256` records them.

## Production broker configuration

The base Compose topology includes local Redis authentication and an internal plaintext Kafka
broker for rehearsal. Use the production overlay for authenticated TLS external Redis and Kafka;
it disables local brokers and removes application dependencies on them. Set the production-only
hostnames, ports and CA paths required by that overlay. It enables Redis certificate validation,
Kafka SASL_SSL/PLAIN, and Kafka hostname verification, with explicit CA files.

Supply the external Redis password in Vault at `secret/local/infrastructure/redis` or in the
protected `redis/password` input before running `site.yml -e external_redis=true`. The password
must match the external server. `seed-kafka.yml -e kafka_credentials_file=/protected/kafka.json`
imports missing per-service KV secrets with CAS=0. The JSON has `identity`, `wallet`, `loan`, and
`collateral` keys, each containing a `jaas_config` string for its externally provisioned principal.
Each principal must have only its service's topic permissions. Existing KV versions are preserved;
rotate credentials through a coordinated native Vault/broker procedure.

```bash
export COMPOSE_FILE=docker-compose.yaml:infrastructure/compose.operations.yaml:infrastructure/compose.production.yaml
docker compose --profile applications config --quiet
docker compose --profile applications up -d --build
```

The operations controller always uses the base database/Vault topology for migration. Do not
manually start migration services or reuse a previous attempt root. The production overlay is a
single-host deployment building block, not an HA database/Vault design. Deployment acceptance
still requires live integration, security, recovery and business end-to-end checks. See the
current validation report for executed checks and remaining acceptance work.

## Identity import

`import-identity.yml` replaces both the Python converter and Java import runner. Supply protected
`source_file`, `mapping_file`, and `approved_mapping_sha256` variables in a protected variable file.
The mapping JSON requires `approved_by`, `approval_reference`, `customers`, and `challenges`.
Already-correct prefixed IDs pass through; historical IDs require explicit mappings. The source
contains `challenges` and `audit` arrays. Audit records retain their `challenge_id` references.

The native PostgreSQL transaction sets the stable owner, locks the destination, refuses populated
challenge/audit/scope tables, binds JSON values as parameters, checks integer/timestamp inputs,
inserts parent rows before audit rows, derives scope rows, checks audit subjects/counts, and rolls
back on any error. Timestamp offsets are required; sub-microsecond digits are truncated before PostgreSQL conversion,
preserving the retired converter's precision rule. An
import is never a normal startup or migration side effect. Preserve approved exports and mappings
in protected backup custody, never in source or CI artifacts.

## Backups and recovery

`backup.yml -e backup_directory=/protected/new-checkpoint` creates four native custom-format
`pg_dump` archives and a native Raft snapshot, all mode 0600, plus checksums and a manifest. Quiesce
writers first when cross-service consistency is required. Snapshot bytes are sensitive, including
Vault's stored manager credentials; encrypt and control backup custody. The manifest starts with
`restore_verified: false`; file creation is not a restore test.

`restore-postgresql.yml` requires an empty schema, the backup's exact SHA-256, a selected service,
and an independently recorded `payguard-recovery-*` target. It restores transactionally under the
stable owner, removes temporary schema-creation permission in `always`, then verifies ownership
and history privacy. Never point this workflow at the existing environment.

For Vault recovery, restore the native Raft snapshot using Vault's documented
`vault operator raft snapshot restore` ceremony on a separate recovery cluster, using source
recovery custody and an explicitly authorized recovery operator. Restoring a snapshot replaces
a cluster's state and changes which unseal keys/tokens apply; it is deliberately not an automatic
normal-startup action. Preserve a combined database/Vault checkpoint: restoring Vault alone can
leave its stored manager passwords inconsistent with the databases. Rehearse the complete pair
before cutover; no successful Raft restore is claimed in this delivery.

For an interrupted migration, keep the receipt and run:

```bash
docker compose run --rm --no-deps operations infrastructure/operations/recover-attempt.yml -e attempt_id=RECORDED_ATTEMPT
```

Recovery validates the exact attempt path/project, stops only recorded containers, revokes only
the private attempt token, and removes only that attempt after confirmed cleanup. Repeated
verified recovery is a no-op. Missing tokens, failed revocation, or lost receipts fail closed.
Inspect native Vault leases and database sessions using the protected receipt; never broadly
revoke service leases to guess which one belonged to the failed run. A crash before any token
sink is recorded, or before the receipt exists, may require operator reconciliation of the
retained lock. Never remove a lock until the related processes and leases are accounted for.

Use `install-timer.yml` on the Linux host to install the user service and 20-minute identity
refresh timer. Supply its non-secret environment variables first. Start `agent-operator` after
the first identity refresh; it owns ongoing operator authentication and token renewal. Refresh
uses wrapped one-hour SecretIDs and atomic replacements, without revoking active application
tokens. Monitor timer failures: once bootstrap material expires and the Agent reaches its token
maximum lifetime, threshold recovery may be necessary.

## Verification

```bash
python3 -m pip install -r infrastructure/tests-requirements.txt
python3 -m unittest discover -s infrastructure/tests -v
# Use the operations container for networked native checks.
docker compose run --rm --no-deps operations infrastructure/operations/tests/integration.yml
docker compose run --rm --no-deps operations infrastructure/operations/tests/agent.yml
docker compose run --rm --no-deps operations infrastructure/operations/tests/applications.yml
```

`tests/recovery.yml` refuses disruption outside a recorded `payguard-it-*` fixture. The GitHub
workflow creates explicit isolated volumes, runs migrations twice, exercises privileges/TLS,
starts packaged applications, and performs the Vault stop/start/unseal scenario. It is an
executable validation workflow, not a record of passing execution. Additional mandatory acceptance
work is listed in the current validation report, including restore, migration interruption,
lease max-TTL replacement, confidentiality, and business end-to-end scenarios.
