-- The history table is private even between individual migration commits.
ALTER DEFAULT PRIVILEGES FOR ROLE identity_owner IN SCHEMA payguard
  REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM identity_runtime;
DO $guard$
BEGIN
  IF to_regclass('payguard.flyway_schema_history') IS NOT NULL THEN
    REVOKE ALL ON payguard.flyway_schema_history FROM identity_runtime;
  END IF;
END
$guard$;
-- Grant only application tables after history hardening; never the metadata table.
DO $grant$
DECLARE relation record;
BEGIN
  FOR relation IN SELECT tablename FROM pg_tables
    WHERE schemaname = 'payguard' AND tablename <> 'flyway_schema_history'
  LOOP
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE payguard.%I TO identity_runtime', relation.tablename);
  END LOOP;
END
$grant$;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payguard TO identity_runtime;
DO $audit$
BEGIN
  IF to_regclass('payguard.identity_audit_log') IS NOT NULL THEN
    REVOKE UPDATE, DELETE ON payguard.identity_audit_log FROM identity_runtime;
  END IF;
END
$audit$;
DO $scope$
BEGIN
  IF to_regclass('payguard.identity_challenge_scope') IS NOT NULL THEN
    REVOKE DELETE ON payguard.identity_challenge_scope FROM identity_runtime;
  END IF;
END
$scope$;
