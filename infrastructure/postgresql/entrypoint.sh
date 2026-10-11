#!/bin/sh
set -eu
mkdir -p /var/lib/postgresql/tls
cp /run/payguard/server.crt /var/lib/postgresql/tls/server.crt
cp /run/payguard/server.key /var/lib/postgresql/tls/server.key
cp /run/payguard/init.sql /docker-entrypoint-initdb.d/010-payguard.sql
chown -R postgres:postgres /var/lib/postgresql/tls /docker-entrypoint-initdb.d/010-payguard.sql
chmod 600 /var/lib/postgresql/tls/server.key /docker-entrypoint-initdb.d/010-payguard.sql
exec docker-entrypoint.sh postgres -c ssl=on \
  -c ssl_cert_file=/var/lib/postgresql/tls/server.crt \
  -c ssl_key_file=/var/lib/postgresql/tls/server.key \
  -c password_encryption=scram-sha-256 \
  -c log_min_error_statement=panic \
  -c log_parameter_max_length=0 \
  -c log_parameter_max_length_on_error=0 \
  -c hba_file=/etc/postgresql/pg_hba.conf
