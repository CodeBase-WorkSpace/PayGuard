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
-- Grant only application tables after history hardening; never the metadata table.
DO $grant$
DECLARE relation record;
BEGIN
  FOR relation IN SELECT tablename FROM pg_tables
    WHERE schemaname = 'payguard' AND tablename <> 'flyway_schema_history'
  LOOP
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE payguard.%I TO wallet_runtime', relation.tablename);
  END LOOP;
END
$grant$;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payguard TO wallet_runtime;
