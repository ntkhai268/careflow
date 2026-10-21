CREATE TABLE identity.auth_rate_windows (
 bucket varchar(120) PRIMARY KEY,
 hits integer NOT NULL,
 expires_at timestamptz NOT NULL
);
CREATE INDEX idx_auth_rate_cleanup ON identity.auth_rate_windows(expires_at);
CREATE TABLE identity.recovery_mail_outbox (
 id uuid PRIMARY KEY,
 challenge_id uuid NOT NULL,
 kind varchar(16) NOT NULL CHECK (kind IN ('CODE','CHANGED')),
 payload bytea,
 attempts integer NOT NULL DEFAULT 0,
 available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at timestamptz NOT NULL,
 state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','SENT','FAILED','EXPIRED')),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_recovery_outbox_pending ON identity.recovery_mail_outbox(available_at) WHERE state='PENDING';
ALTER TABLE identity.refresh_tokens ADD COLUMN absolute_expires_at timestamptz;
UPDATE identity.refresh_tokens SET absolute_expires_at=expires_at;
ALTER TABLE identity.refresh_tokens ALTER COLUMN absolute_expires_at SET NOT NULL;

-- Preserve rolling upgrades: older application versions do not supply this column.
CREATE FUNCTION identity.set_session_absolute_expiry() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.absolute_expires_at IS NULL THEN NEW.absolute_expires_at := NEW.expires_at; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER refresh_absolute_default BEFORE INSERT ON identity.refresh_tokens
 FOR EACH ROW EXECUTE FUNCTION identity.set_session_absolute_expiry();
