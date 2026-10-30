# Long-running operator Agent; bootstrap provisioning creates its scoped AppRole once.
vault {
  address = "https://vault:8200"
  ca_cert = "/etc/vault/ca.crt"
}
pid_file = "/tmp/agent.pid"
auto_auth {
  method "approle" {
    config = {
      role_id_file_path = "/run/operator-auth/role-id"
      secret_id_file_path = "/run/operator-auth/secret-id"
      remove_secret_id_file_after_reading = false
    }
  }
  sink "file" {
    config = { path = "/run/operator/token", mode = 0600 }
  }
}
