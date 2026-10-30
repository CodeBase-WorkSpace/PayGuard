vault {
  address = "https://vault:8200"
  ca_cert = "/etc/vault/ca.crt"
}

exit_after_auth = true

pid_file = "/run/agent-private/agent.pid"

auto_auth {
  method "approle" {
    config = {
      role_id_file_path = "/run/agent-auth/role-id"
      secret_id_file_path = "/run/agent-auth/secret-id"
      remove_secret_id_file_after_reading = false
    }
  }
  sink "file" {
    config = {
      path = "/run/agent-private/token"
      mode = 0600
    }
  }
}

template_config {
  exit_on_retry_failure = true
}

template {
  destination = "/run/agent-handoff/credential.toml"
  perms = 0440
  backup = false
  error_on_missing_key = true
  contents = <<EOT
{{ with secret "database/creds/local-loan-migration" }}
# lease_seconds={{ .LeaseDuration }}
# lease_id={{ .LeaseID }}
[environments.default]
url = "jdbc:postgresql://postgres-loan:5432/payguard_loan?sslmode=verify-full&sslrootcert=/run/agent-ca/ca.crt&currentSchema=payguard"
user = {{ .Data.username | toJSON }}
password = {{ .Data.password | toJSON }}
{{ end }}
EOT
}
