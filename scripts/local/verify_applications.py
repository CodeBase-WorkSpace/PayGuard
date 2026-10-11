"""Boot actual packaged applications with only their own AppRole files and verified CA."""
import argparse
import os
import subprocess
import time
import uuid
from common import DependencyError, LOCAL, ROOT, SERVICES, main, run

JRE_IMAGE = "eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61"


def verify():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service", choices=SERVICES)
    selected = parser.parse_args().service
    for service in SERVICES if selected is None else (selected,):
        module = ROOT / f"payguard-{service}-service"
        jars = list((module / "target").glob(f"payguard-{service}-service-*.jar"))
        if len(jars) != 1:
            raise ValueError("Build actual service JARs before runtime verification")
        name = "payguard-application-probe-" + service + "-" + uuid.uuid4().hex[:8]
        run(["docker", "run", "-d", "--name", name, "--network", "payguard_database",
             "--user", f"{os.getuid()}:{os.getgid()}", "-v", f"{jars[0]}:/app.jar:ro",
             "-v", f"{LOCAL / 'apps' / service}:/run/payguard/approle:ro",
             "-v", f"{LOCAL / 'ca.crt'}:/run/payguard/ca.crt:ro",
             "-e", f"SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-{service}:5432/payguard_{service}?sslmode=verify-full&sslrootcert=/run/payguard/ca.crt&currentSchema=payguard",
             "-e", "VAULT_ADDR=https://vault:8200", "-e", "VAULT_CA_FILE=/run/payguard/ca.crt",
             "-e", "VAULT_ROLE_ID_FILE=/run/payguard/approle/role-id", "-e", "VAULT_SECRET_ID_FILE=/run/payguard/approle/secret-id",
             "-e", "REDIS_HOST=redis", "-e", "KAFKA_BOOTSTRAP_SERVERS=kafka:29092",
             JRE_IMAGE, "java", "-jar", "/app.jar"])
        try:
            port = 8084 if service == "identity" else 8080
            deadline = time.monotonic() + 60
            ready = False
            while time.monotonic() < deadline:
                status = run(["docker", "inspect", "-f", "{{.State.Running}}", name]).strip()
                if status != b"true":
                    break
                try:
                    health = run(["docker", "exec", name, "wget", "-qO-", f"http://127.0.0.1:{port}/actuator/health/readiness"])
                    if b'"status":"UP"' in health:
                        ready = True
                        break
                except DependencyError:
                    pass
                time.sleep(0.2)
            captured = subprocess.run(["docker", "logs", name], capture_output=True, check=True)
            logs = captured.stdout + captured.stderr
            markers = [(LOCAL / "apps" / service / "secret-id").read_bytes().strip(),
                       (LOCAL / "operator/token").read_bytes().strip()]
            if any(marker in logs for marker in markers):
                raise ValueError("Secret-bearing application logs detected")
            if not ready:
                # Only exception class names are emitted, never arbitrary application messages.
                import re
                classes = sorted(set(re.findall(rb"(?:[a-z]+\.)+[A-Za-z]+Exception", logs)))
                print(service + ": readiness failed; exception types " + ", ".join(value.decode() for value in classes), flush=True)
                raise DependencyError()
            print(service + ": actual packaged application readiness UP; scoped AppRole and PostgreSQL TLS active", flush=True)
        finally:
            run(["docker", "stop", "-t", "10", name])
            run(["docker", "rm", name])


if __name__ == "__main__":
    main(verify)
