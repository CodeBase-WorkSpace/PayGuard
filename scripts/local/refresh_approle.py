"""Operator-only SecretID replacement; supervise --watch for extended local rehearsal."""
import argparse
import time
from common import LOCAL, SERVICES, main, operator


def refresh():
    vault = operator()
    vault.post("auth/token/renew-self")
    for service in SERVICES:
        vault.provision("local-" + service, LOCAL / "apps" / service)
        vault.provision("local-" + service + "-migration", LOCAL / "operator" / service)
    vault.provision("local-operator", LOCAL / "operator/approle")
    print("Renewed operator token and atomically replaced scoped SecretID files")


def action():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--watch", action="store_true")
    watch = parser.parse_args().watch
    while True:
        refresh()
        if not watch:
            return
        time.sleep(20 * 60)


if __name__ == "__main__":
    main(action)
