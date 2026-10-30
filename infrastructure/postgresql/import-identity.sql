-- Parameters are bound through psycopg; source values never become SQL syntax.
-- Mapping: customers and challenges map old identifiers to approved prefixed IDs.
-- Timestamps require an explicit UTC offset. PostgreSQL stores microsecond precision.
SET LOCAL search_path TO payguard;
SET LOCAL statement_timeout = '5min';
LOCK TABLE identity_challenge, identity_audit_log, identity_challenge_scope IN ACCESS EXCLUSIVE MODE;
DO $guard$
BEGIN
  IF EXISTS (SELECT 1 FROM identity_challenge) OR EXISTS (SELECT 1 FROM identity_audit_log)
     OR EXISTS (SELECT 1 FROM identity_challenge_scope)
  THEN RAISE EXCEPTION 'Identity import destination is populated'; END IF;
END
$guard$;
CREATE TEMP TABLE pg_temp.import_document ON COMMIT DROP AS
SELECT %(source)s::jsonb AS source, %(mapping)s::jsonb AS mapping;
DO $guard$
DECLARE document jsonb;
BEGIN
  SELECT source INTO document FROM pg_temp.import_document;
  IF jsonb_typeof(document->'challenges') IS DISTINCT FROM 'array'
     OR jsonb_typeof(document->'audit') IS DISTINCT FROM 'array'
  THEN RAISE EXCEPTION 'Export arrays required'; END IF;
  IF EXISTS (
    SELECT 1 FROM jsonb_array_elements(document->'challenges') c
    WHERE jsonb_typeof(c->'id') IS DISTINCT FROM 'string'
       OR jsonb_typeof(c->'user_id') IS DISTINCT FROM 'string'
       OR jsonb_typeof(c->'attempts') IS DISTINCT FROM 'number'
       OR jsonb_typeof(c->'max_attempts') IS DISTINCT FROM 'number'
       OR (c->>'attempts') !~ '^[0-9]+$' OR (c->>'max_attempts') !~ '^[0-9]+$'
       OR (c->>'created_at') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$'
       OR (c->>'expires_at') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$'
       OR (c->>'retention_until') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$'
       OR (c->>'consumed_at' IS NOT NULL AND (c->>'consumed_at') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$')
  ) THEN RAISE EXCEPTION 'Invalid integer or timestamp input'; END IF;
  IF EXISTS (
    SELECT 1 FROM jsonb_array_elements(document->'audit') a
    WHERE a->>'occurred_at' IS NULL OR (a->>'occurred_at') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$'
  ) THEN RAISE EXCEPTION 'Audit timestamp offset required'; END IF;
END
$guard$;
INSERT INTO identity_challenge
(id,user_id,purpose,channel,verification_mode,code_hash,device_binding_hash,attempts,max_attempts,status,created_at,expires_at,consumed_at,retention_until)
SELECT CASE WHEN c->>'id' ~ '^otp_[A-Za-z0-9]{22}$' THEN c->>'id' ELSE mapping->'challenges'->>(c->>'id') END,
       CASE WHEN c->>'user_id' ~ '^cus_[A-Za-z0-9]{22}$' THEN c->>'user_id' ELSE mapping->'customers'->>(c->>'user_id') END,
       c->>'purpose',c->>'channel',c->>'verification_mode',c->>'code_hash',c->>'device_binding_hash',
       (c->>'attempts')::integer,(c->>'max_attempts')::integer,c->>'status',
       regexp_replace(c->>'created_at', '(\.[0-9]{6})[0-9]+', '\1')::timestamptz,regexp_replace(c->>'expires_at', '(\.[0-9]{6})[0-9]+', '\1')::timestamptz,regexp_replace(c->>'consumed_at', '(\.[0-9]{6})[0-9]+', '\1')::timestamptz,regexp_replace(c->>'retention_until', '(\.[0-9]{6})[0-9]+', '\1')::timestamptz
FROM pg_temp.import_document, LATERAL jsonb_array_elements(source->'challenges') c;
INSERT INTO identity_audit_log (id,challenge_id,user_id,action,reason,source_ip,occurred_at)
SELECT (a->>'id')::uuid,
       CASE WHEN a->>'challenge_id' ~ '^otp_[A-Za-z0-9]{22}$' THEN a->>'challenge_id' ELSE mapping->'challenges'->>(a->>'challenge_id') END,
       CASE WHEN a->>'user_id' ~ '^cus_[A-Za-z0-9]{22}$' THEN a->>'user_id' ELSE mapping->'customers'->>(a->>'user_id') END,
       a->>'action',a->>'reason',a->>'source_ip',regexp_replace(a->>'occurred_at', '(\.[0-9]{6})[0-9]+', '\1')::timestamptz
FROM pg_temp.import_document, LATERAL jsonb_array_elements(source->'audit') a;
INSERT INTO identity_challenge_scope (user_id,purpose) SELECT DISTINCT user_id,purpose FROM identity_challenge;
DO $reconcile$
BEGIN
  IF EXISTS (SELECT 1 FROM identity_audit_log a JOIN identity_challenge c ON c.id=a.challenge_id WHERE a.user_id<>c.user_id)
     OR (SELECT count(*) FROM identity_challenge)<>(SELECT jsonb_array_length(source->'challenges') FROM pg_temp.import_document)
     OR (SELECT count(*) FROM identity_audit_log)<>(SELECT jsonb_array_length(source->'audit') FROM pg_temp.import_document)
  THEN RAISE EXCEPTION 'Identity import reconciliation failed'; END IF;
END
$reconcile$;
