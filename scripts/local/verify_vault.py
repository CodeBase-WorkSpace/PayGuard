"""Real isolated TLS dependency tests. Controls only the local rehearsal database/Vault."""
import json
import os
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
from common import DependencyError, JAVA_IMAGE, LOCAL, ROOT, main, run


def verify():
    reports = ROOT / "payguard-shared/target/vault-test-reports"
    control = reports / "control"
    control.mkdir(parents=True, exist_ok=True)
    for name in ("stop.request", "stop.ack", "start.request", "start.ack"):
        (control / name).unlink(missing_ok=True)
    compose = ["docker", "compose", "--env-file", ".env.example"]
    name = "payguard-vault-verification-" + uuid.uuid4().hex[:12]
    command = ["docker", "run", "--rm", "--name", name, "--network", "payguard_database",
               "--user", f"{os.getuid()}:{os.getgid()}", "-v", f"{ROOT}:/workspace",
               "-w", "/workspace/payguard-shared", "-e", "PAYGUARD_REHEARSAL=true", JAVA_IMAGE,
               "java", "-cp", "target/classes:target/test-classes:target/dependency/*:/workspace/payguard-identity-service/target/classes:/workspace/payguard-identity-service/target/test-classes:/workspace/payguard-identity-service/target/dependency/*",
               "org.junit.platform.console.ConsoleLauncher", "execute", "--disable-ansi-colors",
               "--select-class", "infrastructure.vault.VaultDatabaseIT",
               "--select-class", "dev.amg.payguard.identity.verification.infrastructure.security.VaultSecretsIT",
               "--reports-dir", "target/vault-test-reports"]
    began = time.monotonic()
    stopped = False
    with open(os.devnull, "wb") as null:
        process = subprocess.Popen(command, cwd=ROOT, stdout=null, stderr=null)
        try:
            while process.poll() is None:
                if time.monotonic() - began > 240:
                    run(["docker", "stop", "-t", "5", name])
                    raise DependencyError()
                if (control / "stop.request").exists() and not (control / "stop.ack").exists():
                    run(compose + ["stop", "postgres-identity"])
                    stopped = True
                    (control / "stop.ack").write_text("stopped")
                if (control / "start.request").exists() and not (control / "start.ack").exists():
                    run(compose + ["up", "-d", "--wait", "postgres-identity"])
                    stopped = False
                    (control / "start.ack").write_text("healthy")
                time.sleep(0.2)
        finally:
            if stopped:
                run(compose + ["up", "-d", "--wait", "postgres-identity"])
            if process.poll() is None:
                run(["docker", "stop", "-t", "5", name])
                process.wait(timeout=15)
    counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for report in reports.glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        for key in counts:
            counts[key] += int(suite.get(key, "0"))
    print("Vault/PostgreSQL tests:", json.dumps(counts), flush=True)
    if process.returncode or counts != {"tests": 15, "failures": 0, "errors": 0, "skipped": 0}:
        raise DependencyError()
    audit = run(compose + ["exec", "-T", "vault", "cat", "/vault/audit/audit.json"])
    records = [json.loads(line) for line in audit.splitlines() if line.strip()]
    if not any(record.get("error") and record.get("request", {}).get("path", "").startswith("secret/data/local/") for record in records):
        raise ValueError("Denied audit evidence missing")
    if not any(record.get("type") == "response" and not record.get("error") and record.get("request", {}).get("path") == "database/creds/local-identity-runtime" for record in records):
        raise ValueError("Allowed audit evidence missing")
    markers = [b"synthetic-first-pepper-marker-123456789", b"synthetic-second-pepper-marker-123456789",
               b"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"]
    for path in (LOCAL / "operator/token", LOCAL / "apps/identity/secret-id"):
        markers.append(path.read_bytes().strip())
    if any(marker in audit for marker in markers):
        raise ValueError("Audit sensitive-value protection failed")
    if not any(str(record.get("auth", {}).get("client_token", "")).startswith("hmac-sha256:") for record in records):
        raise ValueError("Audit token protection evidence missing")
    for report in reports.glob("TEST-*.xml"):
        if any(marker in report.read_bytes() for marker in markers):
            raise ValueError("Secret-bearing diagnostics found")
    print("Allowed/denied accesses traceable; audit and report secret-marker checks passed")


if __name__ == "__main__":
    main(verify)
