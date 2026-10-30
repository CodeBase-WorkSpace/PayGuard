"""Offline contracts for the native deployment; run with unittest, never for provisioning."""
from pathlib import Path
import hashlib
import json
import re
import tomllib
import unittest
import xml.etree.ElementTree as ET
import yaml
import hcl2

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ("identity", "wallet", "loan", "collateral")

class DeploymentContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compose = yaml.safe_load((ROOT / "docker-compose.yaml").read_text())
        cls.ops = yaml.safe_load((ROOT / "infrastructure/compose.operations.yaml").read_text())

    def test_existing_data_volumes_are_external_and_named(self):
        volumes = self.compose["volumes"]
        self.assertEqual(7, len(volumes))
        for value in volumes.values():
            self.assertTrue(value["external"])
            self.assertIn("payguard_", value["name"])

    def test_postgres_18_volume_and_tls_contract(self):
        for service in SERVICES:
            database = self.compose["services"]["postgres-" + service]
            self.assertTrue(database["image"].startswith("postgres:18-alpine@sha256:"))
            self.assertIn(f"postgres_{service}_data:/var/lib/postgresql", database["volumes"])
            self.assertNotIn("ports", database)
            self.assertIn("POSTGRES_PASSWORD_FILE", database["environment"])
        hba = (ROOT / "infrastructure/postgresql/pg_hba.conf").read_text()
        self.assertNotRegex(hba, r"(?m)^.*\btrust\b")
        self.assertIn("scram-sha-256", hba)
        self.assertIn("refusing initialization", (ROOT / "infrastructure/postgresql/entrypoint.sh").read_text())

    def test_no_bootstrap_credentials_in_application_configuration(self):
        for service in SERVICES:
            application = self.compose["services"][service + "-service"]
            self.assertEqual(["applications"], application["profiles"])
            self.assertEqual("vault", application["entrypoint"][0])
            self.assertNotIn("SPRING_DATASOURCE_PASSWORD", application["environment"])
            self.assertNotIn("VAULT_SECRET_ID_FILE", application["environment"])
            for volume in application["volumes"]:
                self.assertTrue(volume["read_only"])
                self.assertFalse(volume["bind"]["create_host_path"])

    def test_all_hcl_documents_parse(self):
        for path in (ROOT / "infrastructure/vault").rglob("*.hcl"):
            with self.subTest(path=path.name), path.open() as stream:
                hcl2.load(stream)

    def test_agent_owns_authentication_and_runtime_lifecycle(self):
        for service in SERVICES:
            text = (ROOT / f"infrastructure/vault/runtime/{service}.hcl").read_text()
            self.assertIn('restart_on_secret_changes = "always"', text)
            self.assertIn('database/creds/local-' + service + '-runtime', text)
            self.assertIn('SPRING_DATA_REDIS_PASSWORD', text)
            self.assertNotIn('exit_after_auth = true', text)
            self.assertNotIn('tls_skip_verify', text)

    def test_each_flyway_job_is_scoped_and_immutable(self):
        for service in SERVICES:
            job = self.ops["services"]["migrate-" + service]
            self.assertTrue(job["read_only"])
            self.assertEqual(["database"], job["networks"])
            self.assertEqual({"condition": "service_completed_successfully"}, job["depends_on"]["agent-" + service])
            self.assertEqual({"driver": "none"}, job["logging"])
            self.assertTrue(all(m["read_only"] for m in job["volumes"]))

    def test_flyway_disallows_destructive_and_implicit_adoption_commands(self):
        config = tomllib.loads((ROOT / "infrastructure/flyway/flyway.toml").read_text())["flyway"]
        self.assertTrue(config["cleanDisabled"])
        self.assertFalse(config["baselineOnMigrate"])
        self.assertFalse(config["outOfOrder"])
        self.assertFalse(config["createSchemas"])
        self.assertTrue(config["validateOnMigrate"])

    def test_hostile_credentials_remain_single_toml_string_values(self):
        # Agent uses Go toJSON. JSON string escaping is valid TOML for these markers.
        markers = ['quote"marker', 'backslash\\marker', 'line\nbreak', 'x"\ninitSql = "DROP TABLE x', 'tab\tmarker']
        for value in markers:
            data = tomllib.loads("[environments.default]\npassword = " + json.dumps(value) + "\n")
            self.assertEqual(value, data["environments"]["default"]["password"])
            self.assertEqual(["password"], list(data["environments"]["default"]))
        for service in SERVICES:
            text = (ROOT / f"infrastructure/vault/agent/{service}.hcl").read_text()
            self.assertIn(".Data.username | toJSON", text)
            self.assertIn(".Data.password | toJSON", text)
            self.assertNotIn("initSql", text)
            after_connect = (ROOT / f"infrastructure/flyway/callbacks/{service}/afterConnect.sql").read_text()
            self.assertIn(f"SET ROLE {service}_owner", after_connect)

    def test_history_is_never_in_runtime_default_table_grants(self):
        for service in SERVICES:
            for event in ["beforeMigrate", "afterMigrate", "afterMigrateError"]:
                sql = (ROOT / f"infrastructure/flyway/callbacks/{service}/{event}.sql").read_text()
                self.assertIn(f"REVOKE ALL ON payguard.flyway_schema_history FROM {service}_runtime", sql)
                self.assertIn("REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES", sql)
            self.assertIn("tablename <> 'flyway_schema_history'", sql)

    def test_original_versioned_sql_hashes_are_preserved(self):
        for line in (ROOT / "infrastructure/migrations.sha256").read_text().splitlines():
            checksum, name = line.split("  ", 1)
            self.assertEqual(checksum, hashlib.sha256((ROOT / name).read_bytes()).hexdigest(), name)

    def test_no_retired_production_tooling(self):
        self.assertFalse((ROOT / "scripts").exists())
        for name in ["SchemaMigrator", "IdentityDataImporter", "LeasedDataSource"]:
            self.assertFalse((ROOT / f"payguard-shared/src/main/java/infrastructure/vault/{name}.java").exists())
        client = (ROOT / "payguard-shared/src/main/java/infrastructure/vault/VaultClient.java").read_text()
        for endpoint in ["auth/approle/login", "auth/token/renew-self", "auth/token/revoke-self", "sys/leases"]:
            self.assertNotIn(endpoint, client)

    def test_parent_reactor_modules_exist(self):
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        root = ET.parse(ROOT / "pom.xml").getroot()
        for node in root.findall("m:modules/m:module", ns):
            self.assertTrue((ROOT / node.text / "pom.xml").is_file())

    def test_production_brokers_require_authentication_tls_and_hostname_checks(self):
        class Loader(yaml.SafeLoader):
            pass
        Loader.add_constructor("!override", lambda loader, node: loader.construct_mapping(node, deep=True))
        production = yaml.load((ROOT / "infrastructure/compose.production.yaml").read_text(), Loader=Loader)
        self.assertEqual(["local-brokers"], production["services"]["kafka"]["profiles"])
        self.assertEqual(["local-brokers"], production["services"]["redis"]["profiles"])
        for service in SERVICES:
            app = production["services"][service + "-service"]
            self.assertNotIn("kafka", app["depends_on"])
            self.assertNotIn("redis", app["depends_on"])
            self.assertEqual("SASL_SSL", app["environment"]["SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL"])
            self.assertEqual("https", app["environment"]["SPRING_KAFKA_PROPERTIES_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM"])
            self.assertEqual("true", app["environment"]["SPRING_DATA_REDIS_SSL_ENABLED"])

    def test_agent_and_flyway_tmpfs_are_writable_by_the_configured_nonroot_user(self):
        for service in SERVICES:
            for prefix in ["agent-", "migrate-"]:
                tmpfs = self.ops["services"][prefix + service]["tmpfs"][0]
                self.assertIn("uid=${PAYGUARD_LOCAL_UID", tmpfs)
                self.assertIn("gid=${PAYGUARD_LOCAL_GID", tmpfs)
                self.assertIn("mode=0700", tmpfs)

    def test_production_initialization_requires_separate_custodians(self):
        ceremony = (ROOT / "infrastructure/operations/vault-lifecycle.yml").read_text()
        self.assertIn("root_token_pgp_key", ceremony)
        self.assertIn("recovery_pgp_keys", ceremony)
        self.assertIn("unseal_key_files", ceremony)
        self.assertIn("always:", ceremony)

    def test_security_sensitive_ansible_tasks_suppress_output(self):
        for path in (ROOT / "infrastructure/operations").glob("*.yml"):
            plays = yaml.safe_load(path.read_text())
            if not isinstance(plays, list):
                continue
            for play in plays:
                for task in play.get("tasks", []) + play.get("pre_tasks", []):
                    encoded = json.dumps(task)
                    # A secret-bearing action must not print its body on failure.
                    if any(key in task for key in ["ansible.builtin.uri", "community.postgresql.postgresql_query", "community.postgresql.postgresql_user"]):
                        if any(secret in encoded for secret in ["X-Vault-Token", "login_password", "root_token", "secret_id"]):
                            self.assertTrue(task.get("no_log"), (path.name, task.get("name")))

    def test_migration_has_bounded_execution_and_verified_cleanup(self):
        text = (ROOT / "infrastructure/operations/migrate.yml").read_text()
        for value in ["'600'", "token_lookup.json.data.ttl", "revoke-self", "cleanup_pending", "cleanup_verified", "migration-{{ service }}.lock", "tasks/verify-migration.yml"]:
            self.assertIn(value, text)
        self.assertIn("always:", text)

    def test_recovery_requires_exact_receipt_path(self):
        text = (ROOT / "infrastructure/operations/recover-attempt.yml").read_text()
        self.assertIn("receipt.path == runtime_root ~ '/' ~ attempt_id", text)
        self.assertNotIn("receipt.path.startswith", text)
        self.assertIn("cleanup_unknown", text)

if __name__ == "__main__":
    unittest.main()
