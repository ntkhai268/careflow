-- Retire TOTP enrollment; password login remains available for existing accounts.
DELETE FROM identity.mfa_recovery_codes;
DELETE FROM identity.mfa_credentials;
CREATE TABLE identity.login_questions (
 user_id uuid PRIMARY KEY REFERENCES identity.users(id),
 question_id varchar(20) NOT NULL,
 answer_hash varchar(100) NOT NULL
);
CREATE TABLE identity.passkeys (
 credential_id text PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES identity.users(id),
 public_key_cose text NOT NULL,
 signature_count bigint NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_passkeys_user ON identity.passkeys(user_id);
CREATE TABLE identity.login_challenges (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES identity.users(id),
 kind varchar(20) NOT NULL,
 request_json text NOT NULL,
 dpop_jkt varchar(43) NOT NULL,
 expires_at timestamptz NOT NULL
);
CREATE INDEX idx_login_challenges_expiry ON identity.login_challenges(expires_at);
