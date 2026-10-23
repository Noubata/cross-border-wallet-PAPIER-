CREATE SCHEMA auth;
CREATE SCHEMA customer;
CREATE SCHEMA audit;

CREATE TABLE auth.users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auth.refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_id_idx ON auth.refresh_tokens(user_id);

CREATE TABLE customer.profiles (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL UNIQUE,
    preferred_language VARCHAR(2) NOT NULL CHECK (preferred_language IN ('fr', 'en')),
    kyc_status VARCHAR(16) NOT NULL CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit.events (
    id UUID PRIMARY KEY,
    actor_id UUID,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_events_created_at_idx ON audit.events(created_at DESC);
CREATE INDEX audit_events_actor_id_idx ON audit.events(actor_id, created_at DESC);

CREATE FUNCTION audit.reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are append-only';
END;
$$;

CREATE TRIGGER audit_events_immutable
    BEFORE UPDATE OR DELETE ON audit.events
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

CREATE TRIGGER audit_events_no_truncate
    BEFORE TRUNCATE ON audit.events
    FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_mutation();