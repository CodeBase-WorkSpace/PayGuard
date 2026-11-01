# Validation after restoring stashed work

Current source and evidence are documented in [the recovery record](recovery/stashed-work.md).

The recovered reactor packages and compiles its tests. 302 selected Java tests and 18 offline
infrastructure contracts pass. PostgreSQL 18 migration/import SQL checks passed using PGlite;
Docker-backed runtime, TLS, Vault renewal, recovery and business end-to-end acceptance remain
pending. Recovered Wallet formatting fails the full formatting gate, and the unfinished Identity
audit adapter remains an application-startup blocker.

Historical infrastructure-only evidence from merged PR #40 is retained separately in
[infrastructure-before-recovery.md](recovery/infrastructure-before-recovery.md). It must not be
interpreted as validation of the recovered business refactor.
