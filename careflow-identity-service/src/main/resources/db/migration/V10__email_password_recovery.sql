CREATE TABLE identity.password_reset_codes (
 email_fingerprint varchar(64) PRIMARY KEY,
 challenge_id uuid UNIQUE,
 user_id uuid REFERENCES identity.users(id),
 code_hash varchar(100),
 dpop_jkt varchar(43),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_sent_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at timestamptz,
 used_at timestamptz,
 failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
 window_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 send_count integer NOT NULL DEFAULT 0 CHECK (send_count BETWEEN 0 AND 5),
 delivery_state varchar(16) NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX idx_password_reset_cleanup ON identity.password_reset_codes(last_sent_at);
CREATE INDEX idx_password_reset_user ON identity.password_reset_codes(user_id);
