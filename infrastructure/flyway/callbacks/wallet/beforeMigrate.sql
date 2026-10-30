-- The history table is private even between individual migration commits.
ALTER DEFAULT PRIVILEGES FOR ROLE wallet_owner IN SCHEMA payguard
  REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM wallet_runtime;
DO $guard$
BEGIN
  IF to_regclass('payguard.flyway_schema_history') IS NOT NULL THEN
    REVOKE ALL ON payguard.flyway_schema_history FROM wallet_runtime;
  END IF;
END
$guard$;
