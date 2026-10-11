"""Create new protected artifacts. Existing data and artifacts are never overwritten."""
import json
import os
import secrets
from common import LOCAL, SERVICES, main, run, write


def certificate(folder, name, alternatives):
    folder.mkdir(mode=0o700, parents=True)
    run(["openssl", "req", "-new", "-newkey", "rsa:3072", "-nodes",
         "-keyout", str(folder / "server.key"), "-out", str(folder / "server.csr"),
         "-subj", f"/CN={name}"])
    write(folder / "extensions.cnf", "basicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName=" + alternatives + "\n")
    run(["openssl", "x509", "-req", "-in", str(folder / "server.csr"),
         "-CA", str(LOCAL / "ca.crt"), "-CAkey", str(LOCAL / "ca.key"),
         "-CAcreateserial", "-days", "30", "-sha256", "-extfile", str(folder / "extensions.cnf"),
         "-out", str(folder / "server.crt")])
    for path in folder.iterdir():
        path.chmod(0o600)


def sql(service, credentials):
    owner, runtime = f"{service}_owner", f"{service}_runtime"
    return f"""REVOKE ALL ON DATABASE payguard_{service} FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE ROLE {owner} NOLOGIN;
CREATE ROLE {runtime} NOLOGIN;
CREATE ROLE vault_manager LOGIN CREATEROLE PASSWORD '{credentials['manager']}';
CREATE ROLE bootstrap_migrator LOGIN PASSWORD '{credentials['migration']}';
GRANT {owner} TO bootstrap_migrator;
GRANT {owner}, {runtime} TO vault_manager WITH ADMIN TRUE, INHERIT FALSE, SET FALSE;
GRANT CONNECT ON DATABASE payguard_{service} TO {owner}, {runtime}, vault_manager;
CREATE SCHEMA payguard AUTHORIZATION {owner};
GRANT USAGE ON SCHEMA payguard TO {runtime}, vault_manager;
ALTER DEFAULT PRIVILEGES FOR ROLE {owner} IN SCHEMA payguard
 GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO {runtime};
ALTER DEFAULT PRIVILEGES FOR ROLE {owner} IN SCHEMA payguard
 GRANT USAGE, SELECT ON SEQUENCES TO {runtime};
ALTER DEFAULT PRIVILEGES FOR ROLE {owner} REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
"""


def prepare():
    if LOCAL.exists():
        raise ValueError("Refuse existing protected artifacts")
    os.umask(0o077)
    LOCAL.mkdir(parents=True, mode=0o700)
    run(["openssl", "req", "-x509", "-newkey", "rsa:3072", "-nodes", "-sha256", "-days", "30",
         "-subj", "/CN=PayGuard disposable rehearsal CA", "-addext", "basicConstraints=critical,CA:TRUE",
         "-addext", "keyUsage=critical,keyCertSign,cRLSign", "-keyout", str(LOCAL / "ca.key"),
         "-out", str(LOCAL / "ca.crt")])
    (LOCAL / "ca.key").chmod(0o600)
    (LOCAL / "ca.crt").chmod(0o600)
    for service in SERVICES:
        folder = LOCAL / service
        certificate(folder, "postgres-" + service, "DNS:postgres-" + service)
        credentials = {kind: secrets.token_hex(32) for kind in ("admin", "manager", "migration")}
        write(folder / "credentials.json", json.dumps(credentials))
        write(folder / "admin-password", credentials["admin"])
        write(folder / "init.sql", sql(service, credentials))
        (LOCAL / "apps" / service).mkdir(parents=True, mode=0o700)
    certificate(LOCAL / "vault", "vault", "DNS:vault,DNS:localhost,IP:127.0.0.1")
    write(LOCAL / "vault/ca.crt", (LOCAL / "ca.crt").read_text())
    print("Created protected local TLS and bootstrap artifacts; no secret values displayed")


if __name__ == "__main__":
    main(prepare)
