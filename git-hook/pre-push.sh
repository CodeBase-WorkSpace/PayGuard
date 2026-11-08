#!/usr/bin/env bash

# Validate exactly the commits Git is about to push, not staged/uncommitted files.
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
cd "$repository_root"
remote_name="${1:-origin}"

scratch_directory="$(mktemp -d "${TMPDIR:-/tmp}/payguard-pre-push.XXXXXX")"
checkout_directory="$scratch_directory/checkout"

cleanup() {
  git worktree remove --force "$checkout_directory" >/dev/null 2>&1 || true
  rm -rf -- "$scratch_directory"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# For a new remote branch, compare its commits with the remote's default branch.
# If that baseline is unavailable, use the full workflow instead of guessing.
find_new_branch_base() {
  local pushed_commit="$1"
  local candidate_ref candidate_commit

  for candidate_ref in \
    "refs/remotes/$remote_name/HEAD" \
    "refs/remotes/$remote_name/main" \
    "refs/remotes/$remote_name/master"; do
    candidate_commit="$(git rev-parse -q --verify "${candidate_ref}^{commit}" 2>/dev/null || true)"
    if [[ -n "$candidate_commit" ]]; then
      if git merge-base "$candidate_commit" "$pushed_commit"; then
        return 0
      fi
    fi
  done

  return 1
}

run_full_workflow() {
  if ! command -v act >/dev/null 2>&1; then
    echo "ERROR: act is required for full validation." >&2
    return 1
  fi
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker must be running for the full act workflow." >&2
    return 1
  fi

  # Leave act's cache server enabled for workflows that use actions/cache.
  (cd "$checkout_directory" && act pull_request --container-architecture linux/amd64)
}

# Git supplies one line per ref: local-ref local-oid remote-ref remote-oid.
declare -a local_refs=() local_commits=() remote_commits=()
while read -r local_ref local_commit remote_ref remote_commit; do
  [[ -n "${local_commit:-}" ]] || continue
  if [[ "$local_ref" == "(delete)" || "$local_commit" =~ ^0+$ ]]; then
    echo "Skipping deleted ref $remote_ref."
    continue
  fi

  local_refs+=("$local_ref")
  local_commits+=("$local_commit")
  remote_commits+=("$remote_commit")
done

if [[ ${#local_commits[@]} -eq 0 ]]; then
  echo "No commits to validate."
  exit 0
fi

for index in "${!local_commits[@]}"; do
  local_ref="${local_refs[$index]}"
  local_commit="${local_commits[$index]}"
  remote_commit="${remote_commits[$index]}"
  full_reason=""
  base_commit=""

  echo "Checking pushed ref $local_ref ($local_commit)..."

  if [[ "${PAYGUARD_FULL_CHECK:-0}" == "1" ]]; then
    full_reason="explicit full-validation request"
  elif [[ "$local_ref" != refs/heads/* && "$local_ref" != "HEAD" ]]; then
    full_reason="tag or non-branch ref"
  elif [[ "$remote_commit" =~ ^0+$ ]]; then
    base_commit="$(find_new_branch_base "$local_commit")" || full_reason="no reliable baseline for the new remote branch"
  elif git cat-file -e "${remote_commit}^{commit}" 2>/dev/null; then
    base_commit="$(git rev-parse "${remote_commit}^{commit}")"
  else
    full_reason="remote commit is not available locally"
  fi

  declare -A changed_modules=()
  if [[ -z "$full_reason" ]]; then
    if ! git diff --no-renames --name-only -z "$base_commit" "$local_commit" > "$scratch_directory/changes"; then
      full_reason="unable to calculate pushed changes"
    else
      while IFS= read -r -d '' changed_path; do
        case "$changed_path" in
          payguard-wallet/*|payguard-identity/*|payguard-loan/*|payguard-collateral/*|payguard-gateway/*)
            changed_modules["${changed_path%%/*}"]=1
            ;;
          payguard-shared/*|payguard-proto/*)
            full_reason="shared code or protocol definitions changed"
            ;;
          *)
            full_reason="repository-wide or unclassified change: $changed_path"
            ;;
        esac
      done < "$scratch_directory/changes"
    fi
  fi

  # Fixed ordering keeps the log and task invocation deterministic.
  tasks=()
  for module in payguard-identity payguard-wallet payguard-loan payguard-collateral payguard-gateway; do
    if [[ -n "${changed_modules[$module]:-}" ]]; then
      tasks+=(":${module}:check")
    fi
  done

  if [[ -z "$full_reason" && ${#tasks[@]} -eq 0 ]]; then
    echo "No changed files requiring validation."
    continue
  fi

  # A detached worktree guarantees that checks use the pushed commit, not any
  # additional modifications sitting in the user's current working tree.
  git worktree add --detach "$checkout_directory" "$local_commit" >/dev/null

  if [[ ! -f "$checkout_directory/build.gradle.kts" ]]; then
    echo "ERROR: pushed commit $local_commit has no root build.gradle.kts. Commit the Gradle migration and workflow changes before pushing." >&2
    exit 1
  fi

  if [[ -z "$full_reason" ]]; then
    if [[ ! -f "$checkout_directory/settings.gradle.kts" && ! -f "$checkout_directory/settings.gradle" ]]; then
      full_reason="no root multi-project Gradle settings file"
    elif [[ ! -f "$checkout_directory/gradlew" ]] && ! command -v gradle >/dev/null 2>&1; then
      full_reason="no Gradle wrapper or Gradle executable"
    fi
  fi

  if [[ -n "$full_reason" ]]; then
    echo "Running full PR workflow ($full_reason)."
    run_full_workflow
  else
    echo "Running selected Gradle checks with the local build cache: ${tasks[*]}"
    if [[ -f "$checkout_directory/gradlew" ]]; then
      (cd "$checkout_directory" && bash ./gradlew --build-cache "${tasks[@]}")
    else
      (cd "$checkout_directory" && gradle --build-cache "${tasks[@]}")
    fi
  fi

  git worktree remove --force "$checkout_directory" >/dev/null
done

echo "Pre-push validation passed."
