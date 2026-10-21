ALTER TABLE identity.refresh_tokens ADD COLUMN dpop_jkt varchar(43);
CREATE TABLE identity.mfa_credentials (
    user_id uuid PRIMARY KEY REFERENCES identity.users(id),
    secret_ciphertext text NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    pending_expires_at timestamptz,
    last_counter bigint NOT NULL DEFAULT -1,
    failed_attempts integer NOT NULL DEFAULT 0,
    locked_until timestamptz
);
CREATE TABLE identity.mfa_recovery_codes (
    user_id uuid NOT NULL REFERENCES identity.users(id),
    code_hash varchar(64) NOT NULL,
    PRIMARY KEY (user_id, code_hash)
);
CREATE TABLE identity.dpop_replays (
    proof_hash varchar(64) PRIMARY KEY,
    expires_at timestamptz NOT NULL
);
CREATE INDEX idx_dpop_replays_expiry ON identity.dpop_replays(expires_at);
CREATE TABLE identity.security_events (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identity.users(id),
    event_type varchar(48) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
