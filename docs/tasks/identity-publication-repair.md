# Identity publication repair

## Context and scope

The owner's local commit `68e4bd0` deleted the retired OTP module, but its committed parent POM still selected that module. The pre-push hook checks a detached committed worktree, so it failed before formatting or compilation. GitHub separately rejected a direct main push because a pull request and CodeQL results are required.

The repair branch `amg-complete-identity-service` starts from local main and retains that deletion commit. Its companion change registers Identity and includes the 36 previously local application/bootstrap/adapter/test files needed by the existing service. Five unused future ID classes, CLAUDE.md, an unrelated ADR rename and protected local artifacts remain outside this publication. The Flyway environment configuration fix is committed separately.

## Checklist

- [x] Inspect the committed snapshot, working tree, migration notes and CI.
- [x] Preserve the original deletion commit and create an amg branch from main.
- [x] Record original file hashes and tracked edits in the ignored local repair directory.
- [ ] Commit the complete Identity module selection and source dependency closure.
- [ ] Verify the committed source from a clean worktree; resolve actual formatting, compilation, test and analysis failures without weakening checks.
- [ ] Commit the independently verified native Flyway environment fix and documentation.
- [ ] Push normally, open a PR to main and inspect GitHub quality/security/CodeQL results.
- [ ] Leave local main aligned with remote main, preserving the owner's deletion commit on the repair branch.

## Evidence boundary

Verification is pending for this publication. Earlier 95-test migration results were from a different full working checkout and do not establish this branch's result. This task does not implement new authentication, migrate live Oracle data, deploy services, disable repository rules, rebase history or force-push.
