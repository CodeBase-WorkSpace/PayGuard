-- The history table is private even between individual migration commits.
ALTER DEFAULT PRIVILEGES FOR ROLE collateral_owner IN SCHEMA payguard
  REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM collateral_runtime;
DO $guard$
BEGIN
  IF to_regclass('payguard.flyway_schema_history') IS NOT NULL THEN
    REVOKE ALL ON payguard.flyway_schema_history FROM collateral_runtime;
  END IF;
END
$guard$;
