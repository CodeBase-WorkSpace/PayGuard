"""Run supplied Flyway schemas with separate migration leases, never runtime privileges."""
import argparse
import os
from pathlib import Path
from common import JAVA_IMAGE, LOCAL, ROOT, Vault, main, read, run


def migrate():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("service", choices=("identity", "loan", "collateral"))
    parser.add_argument("--import-identity", type=Path)
    arguments = parser.parse_args()
    service = arguments.service
    imported = arguments.import_identity
    if imported is not None:
        imported = imported.resolve()
        if service != "identity" or not imported.is_file() or not imported.is_relative_to(LOCAL):
            raise ValueError("Explicit protected Identity export required")
    scope = LOCAL / "operator" / service
    vault = Vault()
    auth = vault.post("auth/approle/login", {
        "role_id": read(scope / "role-id"), "secret_id": read(scope / "secret-id")})
    vault.token = auth["auth"]["client_token"]
    try:
        credentials = vault.get("database/creds/local-" + service + "-migration")["data"]
        environment = os.environ.copy()
        environment.update({
            "PAYGUARD_MIGRATION_SERVICE": service,
            "PAYGUARD_MIGRATION_JDBC_URL": f"jdbc:postgresql://postgres-{service}:5432/payguard_{service}?sslmode=verify-full&sslrootcert=/workspace/.local/payguard/ca.crt&currentSchema=payguard",
            "PAYGUARD_MIGRATION_USERNAME": credentials["username"],
            "PAYGUARD_MIGRATION_PASSWORD": credentials["password"]})
        command = ["docker", "run", "--rm", "--network", "payguard_database",
                   "--user", f"{os.getuid()}:{os.getgid()}", "-v", f"{ROOT}:/workspace:ro", "-w", "/workspace"]
        for name in ("PAYGUARD_MIGRATION_SERVICE", "PAYGUARD_MIGRATION_JDBC_URL", "PAYGUARD_MIGRATION_USERNAME", "PAYGUARD_MIGRATION_PASSWORD"):
            command += ["-e", name]
        command += [JAVA_IMAGE, "java", "-cp", "payguard-shared/target/classes:payguard-shared/target/dependency/*"]
        entry = ["infrastructure.vault.SchemaMigrator"] if imported is None else [
            "infrastructure.vault.IdentityDataImporter", "/workspace/" + str(imported.relative_to(ROOT))]
        run(command + entry, env=environment)
        print(f"{service}: separately privileged migration/import completed")
    finally:
        vault.post("auth/token/revoke-self")


if __name__ == "__main__":
    main(migrate)
