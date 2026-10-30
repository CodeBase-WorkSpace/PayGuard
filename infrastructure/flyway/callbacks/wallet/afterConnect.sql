-- Flyway 13 uses afterConnect in place of the removed initSql setting.
SET ROLE wallet_owner;
SET search_path TO payguard;
SET statement_timeout = '8min';
SET lock_timeout = '30s';
