#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
hook_path="$(git rev-parse --git-path hooks/pre-push)"
mkdir -p "$(dirname "$hook_path")"
cat > "$hook_path" <<'HOOK'
#!/usr/bin/env bash
set -euo pipefail
exec bash "$(git rev-parse --show-toplevel)/git-hook/pre-push.sh" "$@"
HOOK
chmod +x "$hook_path"
echo "Installed pre-push loader at $hook_path"
