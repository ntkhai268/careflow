-- Separately verify the email code before allowing a new password.
ALTER TABLE identity.password_reset_codes ADD COLUMN verified_at timestamptz;
ALTER TABLE identity.password_reset_codes ADD COLUMN reset_token_hash varchar(64);
ALTER TABLE identity.password_reset_codes ADD COLUMN reset_expires_at timestamptz;
