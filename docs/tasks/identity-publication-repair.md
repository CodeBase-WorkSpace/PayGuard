# Identity publication repair

## Context and scope

The owner's local commit `68e4bd0` deleted the retired OTP module, but its committed parent POM still selected that module. The pre-push hook checks a detached committed worktree, so it failed before formatting or compilation. GitHub separately rejected a direct main push because a pull request and CodeQL results are required.

The repair branch `amg-complete-identity-service` starts from local main and retains that deletion commit. Its companion change registers Identity and includes the 36 previously local application/bootstrap/adapter/test files needed by the existing service. Five unused future ID classes, CLAUDE.md, an unrelated ADR rename and protected local artifacts remain outside this publication. The Flyway environment configuration fix is committed separately.

## Checklist

- [x] Inspect the committed snapshot, working tree, migration notes and CI.
- [x] Preserve the original deletion commit and create an amg branch from main.
- [x] Record original file hashes and tracked edits in the ignored local repair directory.
- [x] Commit the complete Identity module selection and source dependency closure (`23f4a77`).
- [x] Verify the committed source from a clean worktree; resolve actual formatting, compilation, test and analysis failures without weakening checks.
- [x] Commit the independently verified native Flyway environment fix and documentation separately.
- [ ] Push normally, open a PR to main and inspect GitHub quality/security/CodeQL results.
- [x] Leave local main aligned with remote main, preserving the owner's deletion commit on the repair branch.

## Evidence boundary

Local full verification passed for this publication; GitHub checks are pending. Earlier 95-test migration results were from a different full working checkout and do not establish this branch's result. This task does not implement new authentication, migrate live Oracle data, deploy services, disable repository rules, rebase history or force-push.

## First clean verification and bounded repairs

`./mvnw --batch-mode --no-transfer-progress verify` on committed `2a71a7d` executed 91 tests, with four failures and zero errors/skips. Two existing test classes reproduced mutable snapshot hashing and pre-creation verification regressions. The repair restores inherited immutable-identity equality/hashing and rejects pre-creation active verification before checking hashes or recording attempts. Tests retain their assertions. OtpId's private field is renamed to avoid the existing PMD field/accessor finding; the public value() contract and identifier bytes are preserved. The five unpublished future identifier classes are outside this branch.

Local main now tracks origin/main at `61832f9`; the owner deletion commit remains an ancestor of the repair branch. Live GitHub branch rules were inspected: pull requests and CodeQL scanning are required.

## Current local verification

The same full Maven command on committed `fd29d35` in `/tmp/payguard-identity-repair-verification` exited 0: **91 tests, zero failures/errors/skips**. Identity executed 74; Shared 4, Proto 1, Gateway 2, Wallet 1, Loan 5 and Collateral 4. Spotless, Checkstyle, PMD, CPD and SpotBugs passed without disabling checks. Identity's 14 real PostgreSQL infrastructure tests and 11 application contracts passed, including competing verification/issuance and attempt/replay boundaries. Reports were inspected under each module's target/surefire-reports; the command log is `/tmp/payguard-identity-repair-verify.log`.

Native Flyway environment configuration was separately verified against disposable PostgreSQL 17.11: Loan V1 applied; five tables/two sequences were owned by loan_owner; omission of FLYWAY_INIT_SQL failed with the intended owner-selection error. The disposable containers and their own anonymous volumes were removed. Existing PostgreSQL/Vault infrastructure was not involved in those configuration checks.

The publication excludes protected local artifacts and actual local credential markers; changed tracked files were checked without printing those values. GitHub CodeQL and the mandatory database-security/image jobs still need their own remote results.
