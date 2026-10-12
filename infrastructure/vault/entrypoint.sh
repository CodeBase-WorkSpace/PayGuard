#!/bin/sh
set -eu
mkdir -p /vault/tls /vault/audit
cp /run/payguard/server.crt /vault/tls/server.crt
cp /run/payguard/server.key /vault/tls/server.key
cp /run/payguard/ca.crt /vault/tls/ca.crt
chown -R vault:vault /vault/tls /vault/data /vault/audit
chmod 700 /vault/tls /vault/audit
chmod 600 /vault/tls/server.key
exec /usr/local/bin/docker-entrypoint.sh server
