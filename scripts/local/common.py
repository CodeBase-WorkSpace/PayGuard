"""Protected local-rehearsal I/O; never include dependency responses in diagnostics."""
import json
import os
from pathlib import Path
import ssl
import subprocess
import tempfile
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / ".local/payguard"
SERVICES = ("wallet", "loan", "collateral", "identity")
JAVA_IMAGE = "eclipse-temurin:25-jdk-alpine@sha256:3fd2d245c4e0eba615fe366a71b8bd25f5db7104f53e4026b24bf508b880bd2a"


class DependencyError(RuntimeError):
    def __init__(self, status=0):
        self.status = status
        super().__init__("Local dependency unavailable or access denied; details suppressed")


def write(path, value):
    """Atomic replacement avoids partially read SecretIDs during reauthentication."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    descriptor, temporary = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(descriptor, "w") as output:
            output.write(value)
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def read(path):
    return Path(path).read_text().strip()


def run(arguments, **kwargs):
    result = subprocess.run(arguments, cwd=ROOT, capture_output=True, **kwargs)
    if result.returncode:
        raise DependencyError()
    return result.stdout


class Vault:
    def __init__(self, token=""):
        self.token = token
        port = int(os.environ.get("VAULT_PORT", "8200"))
        self.address = f"https://127.0.0.1:{port}/v1/"
        self.http = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=LOCAL / "ca.crt")),
        )

    def request(self, method, path, body=None, wrap=False):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["X-Vault-Token"] = self.token
        if wrap:
            headers["X-Vault-Wrap-TTL"] = "60s"
        payload = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.address + path, payload, headers, method=method)
        try:
            with self.http.open(request, timeout=10) as response:
                data = response.read()
                return json.loads(data) if data else {}
        except urllib.error.HTTPError as error:
            raise DependencyError(error.code) from None
        except (OSError, ValueError):
            raise DependencyError() from None

    def get(self, path):
        return self.request("GET", path)

    def post(self, path, body=None):
        return self.request("POST", path, {} if body is None else body)

    def provision(self, role, folder):
        role_id = self.get(f"auth/approle/role/{role}/role-id")["data"]["role_id"]
        wrapped = self.request("POST", f"auth/approle/role/{role}/secret-id", {}, wrap=True)
        one_use = Vault(wrapped["wrap_info"]["token"])
        secret_id = one_use.post("sys/wrapping/unwrap")["data"]["secret_id"]
        write(folder / "role-id", role_id)
        write(folder / "secret-id", secret_id)


def operator():
    return Vault(read(LOCAL / "operator/token"))


def main(action):
    try:
        action()
    except (DependencyError, OSError, ValueError, KeyError):
        raise SystemExit("Local rehearsal failed; protected dependency details suppressed") from None
