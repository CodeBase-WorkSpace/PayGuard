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
