#!/bin/sh
set -eu
# Refuse a different existing layout instead of initializing an empty replacement cluster.
# PGDATA may be overridden only after inspecting the existing volume read-only.
: "${PGDATA:=/var/lib/postgresql/18/docker}"
for version in $(find /var/lib/postgresql -name PG_VERSION -type f); do
  if [ "$version" != "$PGDATA/PG_VERSION" ] || [ "$(cat "$version")" != 18 ]; then
    echo 'Existing PostgreSQL layout/version differs from PGDATA; refusing initialization' >&2
    exit 1
  fi
done
mkdir -p /run/postgres-tls
cp /run/payguard/server.crt /run/postgres-tls/server.crt
cp /run/payguard/server.key /run/postgres-tls/server.key
chown -R postgres:postgres /run/postgres-tls
chmod 700 /run/postgres-tls
chmod 600 /run/postgres-tls/server.key
exec docker-entrypoint.sh postgres -c ssl=on \
  -c ssl_cert_file=/run/postgres-tls/server.crt \
  -c ssl_key_file=/run/postgres-tls/server.key \
  -c password_encryption=scram-sha-256 \
  -c log_min_error_statement=panic \
  -c log_parameter_max_length=0 \
  -c log_parameter_max_length_on_error=0 \
  -c hba_file=/etc/postgresql/pg_hba.conf
