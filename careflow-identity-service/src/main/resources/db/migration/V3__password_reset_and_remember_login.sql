ALTER TABLE identity.refresh_tokens
    ADD COLUMN persistent_session BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE identity.password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_password_reset_tokens_user_active
    ON identity.password_reset_tokens (user_id, used_at);
CREATE INDEX idx_password_reset_tokens_expires_at
    ON identity.password_reset_tokens (expires_at);
