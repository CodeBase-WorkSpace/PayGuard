"""Explicit local operator bootstrap/recovery, verified HTTPS and protected artifacts."""
import argparse
import base64
import json
import secrets
import time
from common import DependencyError, LOCAL, SERVICES, Vault, main, read, write


def rule(path, capabilities):
    return f'path "{path}" {{ capabilities = {json.dumps(capabilities)} }}\n'


def own_token_policy():
    return (rule("auth/token/lookup-self", ["read"])
            + rule("auth/token/renew-self", ["update"])
            + rule("auth/token/revoke-self", ["update"]))


def runtime_policy(service):
    role = "local-" + service + "-runtime"
    policy = own_token_policy() + rule("database/creds/" + role, ["read"])
    for operation in ("renew", "revoke"):
        policy += f'''path "sys/leases/{operation}" {{
 capabilities = ["update"]
 allowed_parameters = {{ "lease_id" = ["database/creds/{role}/*"], "increment" = ["*"] }}
}}\n'''
    if service == "identity":
        policy += rule("secret/data/local/identity/otp-keys", ["read"])
        policy += rule("secret/data/local/identity/authenticators/*", ["read"])
    return policy


def operator_policy():
    policy = own_token_policy()
    for path in ("sys/mounts", "sys/auth", "sys/audit"):
        policy += rule(path, ["read", "sudo"])
    for path in ("sys/mounts/secret", "sys/mounts/database", "sys/auth/approle",
                 "sys/audit/file", "sys/seal", "sys/policies/acl/local-*",
                 "auth/token/create-orphan", "sys/leases/revoke-prefix/database/creds/local-*"):
        policy += rule(path, ["create", "read", "update", "sudo"])
    for path in ("database/config/local-*", "database/roles/local-*", "database/rotate-root/local-*",
                 "auth/approle/role/local-*", "secret/data/local/*",
                 "secret/destroy/local/*"):
        policy += rule(path, ["create", "read", "update", "delete", "list"])
    for service in SERVICES:
        policy += rule("database/creds/local-" + service + "-migration", ["read"])
    for operation in ("renew", "revoke"):
        policy += rule("sys/leases/" + operation, ["update"])
    return policy


def recover(vault, initialization):
    progress = vault.get("sys/generate-root/attempt")
    if progress.get("started"):
        raise ValueError("An operator recovery is already in progress")
    started = vault.post("sys/generate-root/attempt")
    try:
        result = {}
        for key in initialization["keys"][:2]:
            result = vault.post("sys/generate-root/update", {"key": key, "nonce": started["nonce"]})
        if not result.get("complete"):
            raise ValueError("Recovery threshold not met")
        encoded = result.get("encoded_token") or result.get("encoded_root_token")
        raw = base64.b64decode(encoded + "=" * (-len(encoded) % 4))
        otp = started["otp"].encode()
        return Vault(bytes(left ^ right for left, right in zip(raw, otp)).decode())
    except Exception:
        vault.request("DELETE", "sys/generate-root/attempt")
        raise


def configure(vault):
    vault.post("sys/policies/acl/local-operator", {"policy": operator_policy()})
    for path, body in (("sys/mounts/secret", {"type": "kv", "options": {"version": "2"}}),
                       ("sys/mounts/database", {"type": "database"}),
                       ("sys/auth/approle", {"type": "approle"}),
                       ("sys/audit/file", {"type": "file", "options": {"file_path": "/vault/audit/audit.json"}})):
        listing = "sys/auth" if path.startswith("sys/auth") else "sys/audit" if path.startswith("sys/audit") else "sys/mounts"
        name = path.rsplit("/", 1)[1] + "/"
        existing = vault.get(listing)
        if name not in existing.get("data", existing):
            vault.post(path, body)
    vault.post("sys/policies/acl/local-operator", {"policy": operator_policy()})
    for service in SERVICES:
        name = "local-" + service
        credentials = json.loads(read(LOCAL / service / "credentials.json"))
        try:
            vault.get("database/config/" + name)
        except DependencyError as failure:
            if failure.status != 404:
                raise
            vault.post("database/config/" + name, {
                "plugin_name": "postgresql-database-plugin", "allowed_roles": [name + "-runtime", name + "-migration"],
                "connection_url": "postgresql://{{username}}:{{password}}@postgres-" + service + ":5432/payguard_" + service + "?sslmode=verify-full&sslrootcert=/vault/tls/ca.crt",
                "username": "vault_manager", "password": credentials["manager"], "verify_connection": True})
            vault.post("database/rotate-root/" + name)
        for kind, ttl, maximum in (("runtime", "1h", "4h"), ("migration", "15m", "30m")):
            group = service + ("_runtime" if kind == "runtime" else "_owner")
            role = name + "-" + kind
            timeouts = (''' ALTER ROLE "{{name}}" SET statement_timeout='10s';
 ALTER ROLE "{{name}}" SET lock_timeout='10s';
 ALTER ROLE "{{name}}" SET idle_in_transaction_session_timeout='10s';''' if kind == "runtime" else "")
            vault.post("database/roles/" + role, {
                "db_name": name,
                "creation_statements": [f'''CREATE ROLE "{{{{name}}}}" WITH LOGIN PASSWORD '{{{{password}}}}' VALID UNTIL '{{{{expiration}}}}'; GRANT {group} TO "{{{{name}}}}";''' + timeouts],
                "revocation_statements": [f'REVOKE {group} FROM "{{{{name}}}}"; DROP ROLE IF EXISTS "{{{{name}}}}";'],
                "renew_statements": ['ALTER ROLE "{{name}}" VALID UNTIL \'{{expiration}}\';'],
                "default_ttl": ttl, "max_ttl": maximum})
            policy = runtime_policy(service) if kind == "runtime" else own_token_policy() + rule("database/creds/" + role, ["read"])
            policy_name = name if kind == "runtime" else name + "-migration"
            vault.post("sys/policies/acl/" + policy_name, {"policy": policy})
            vault.post("auth/approle/role/" + policy_name, {
                "token_policies": [policy_name], "secret_id_ttl": "1h", "secret_id_num_uses": 0,
                "token_ttl": "20m", "token_max_ttl": "4h", "token_no_default_policy": True})
            destination = LOCAL / "apps" / service if kind == "runtime" else LOCAL / "operator" / service
            vault.provision(policy_name, destination)
    vault.post("auth/approle/role/local-operator", {
        "token_policies": ["local-operator"], "secret_id_ttl": "1h", "secret_id_num_uses": 0,
        "token_ttl": "20m", "token_max_ttl": "4h", "token_no_default_policy": True})
    vault.provision("local-operator", LOCAL / "operator/approle")
    try:
        vault.get("secret/data/local/identity/otp-keys")
    except DependencyError as failure:
        if failure.status != 404:
            raise
        vault.post("secret/data/local/identity/otp-keys", {
            "data": {"current": "localInitial", "keys": {"localInitial": secrets.token_urlsafe(48)}}})


def bootstrap():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recover-operator", action="store_true")
    parser.add_argument("--diagnostics", action="store_true", help="Display only failed API path/status; never response bodies or credentials")
    arguments = parser.parse_args()
    if arguments.diagnostics:
        request = Vault.request

        def diagnosed(self, method, path, *args, **kwargs):
            try:
                return request(self, method, path, *args, **kwargs)
            except DependencyError as failure:
                print(f"Failed bootstrap API: {method} {path}, HTTP {failure.status}", flush=True)
                raise

        Vault.request = diagnosed
    vault = Vault()
    deadline = time.monotonic() + 45
    while True:
        try:
            status = vault.get("sys/init")
            break
        except DependencyError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.2)
    temporary_root = LOCAL / "operator/bootstrap-root"
    if not status["initialized"]:
        initialized = vault.post("sys/init", {"secret_shares": 3, "secret_threshold": 2})
        write(temporary_root, initialized.pop("root_token"))
        write(LOCAL / "operator/initialization.json", json.dumps(initialized))
    initialization = json.loads(read(LOCAL / "operator/initialization.json"))
    if vault.get("sys/seal-status")["sealed"]:
        for key in initialization["keys"][:2]:
            vault.post("sys/unseal", {"key": key})
    while not vault.get("sys/leader").get("is_self"):
        if time.monotonic() >= deadline:
            raise DependencyError()
        time.sleep(0.2)
    root = False
    if temporary_root.exists():
        vault = Vault(read(temporary_root))
        root = True
    elif arguments.recover_operator:
        vault = recover(vault, initialization)
        root = True
    else:
        vault = Vault(read(LOCAL / "operator/token"))
        vault.post("auth/token/renew-self")
    try:
        if root:
            vault.post("sys/policies/acl/local-operator", {"policy": operator_policy()})
            issued = vault.post("auth/token/create-orphan", {"policies": ["local-operator"], "ttl": "8h", "renewable": True})
            write(LOCAL / "operator/token", issued["auth"]["client_token"])
        configure(vault)
    finally:
        if root:
            vault.post("auth/token/revoke-self")
            temporary_root.unlink(missing_ok=True)
    print("Vault initialized/unsealed and scoped local identities provisioned; no root token retained")


if __name__ == "__main__":
    main(bootstrap)
