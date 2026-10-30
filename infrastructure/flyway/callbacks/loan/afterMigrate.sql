-- The history table is private even between individual migration commits.
ALTER DEFAULT PRIVILEGES FOR ROLE loan_owner IN SCHEMA payguard
  REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM loan_runtime;
DO $guard$
BEGIN
  IF to_regclass('payguard.flyway_schema_history') IS NOT NULL THEN
    REVOKE ALL ON payguard.flyway_schema_history FROM loan_runtime;
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
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE payguard.%I TO loan_runtime', relation.tablename);
  END LOOP;
END
$grant$;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payguard TO loan_runtime;
DO $audit$
BEGIN
  IF to_regclass('payguard.loan_audit_log') IS NOT NULL THEN
    REVOKE UPDATE, DELETE ON payguard.loan_audit_log FROM loan_runtime;
  END IF;
END
$audit$;
