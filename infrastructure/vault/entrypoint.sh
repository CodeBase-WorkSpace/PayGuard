#!/bin/sh
set -eu
mkdir -p /vault/tls /vault/audit /vault/data
cp /run/payguard/server.crt /vault/tls/server.crt
cp /run/payguard/server.key /vault/tls/server.key
cp /run/payguard/ca.crt /vault/tls/ca.crt
# Only top-level ownership is reconciled; existing Raft contents are retained.
chown vault:vault /vault/tls /vault/audit /vault/data
chown vault:vault /vault/tls/*
chmod 700 /vault/tls /vault/audit
chmod 600 /vault/tls/server.key
exec su-exec vault vault server -config=/vault/config/server.hcl
