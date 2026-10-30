# Agent owns AppRole authentication and all database-lease renewals.
# Child credentials are injected at exec, absent from Docker Config.Env.
vault {
  address = "https://vault:8200"
  ca_cert = "/etc/vault/ca.crt"
}
pid_file = "/run/agent/agent.pid"
auto_auth {
  method "approle" {
    config = {
      role_id_file_path = "/run/agent-auth/role-id"
      secret_id_file_path = "/run/agent-auth/secret-id"
      remove_secret_id_file_after_reading = false
    }
  }
  sink "file" {
    config = { path = "/run/agent/token", mode = 0600 }
  }
}
template_config {
  exit_on_retry_failure = true
  static_secret_render_interval = "30s"
}
env_template "SPRING_DATASOURCE_USERNAME" {
  contents = "{{ with secret \"database/creds/local-collateral-runtime\" }}{{ .Data.username }}{{ end }}"
  error_on_missing_key = true
}
env_template "SPRING_DATASOURCE_PASSWORD" {
  contents = "{{ with secret \"database/creds/local-collateral-runtime\" }}{{ .Data.password }}{{ end }}"
  error_on_missing_key = true
}
env_template "SPRING_DATA_REDIS_PASSWORD" {
  contents = "{{ with secret \"secret/data/local/infrastructure/redis\" }}{{ .Data.data.password }}{{ end }}"
  error_on_missing_key = true
}
exec {
  command = ["java", "-jar", "/app/service.jar"]
  restart_on_secret_changes = "always"
  restart_stop_signal = "SIGTERM"
}
