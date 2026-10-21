ALTER TABLE identity.users ADD COLUMN email_verified_at timestamptz;
-- Existing internal/demo accounts retain their provenance; no fabricated verification timestamps.
CREATE TABLE identity.signup_challenges (
 email_fingerprint varchar(64) PRIMARY KEY,
 request_id uuid UNIQUE,
 email varchar(255),
 username varchar(50),
 password_hash varchar(255),
 code_hash varchar(100),
 dpop_jkt varchar(43),
 eligible boolean NOT NULL DEFAULT false,
 expires_at timestamptz,
 used_at timestamptz,
 failed_attempts integer NOT NULL DEFAULT 0,
 delivery_state varchar(16) NOT NULL DEFAULT 'PENDING',
 last_sent_at timestamptz,
 window_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 send_count integer NOT NULL DEFAULT 0
);
ALTER TABLE identity.recovery_mail_outbox DROP CONSTRAINT recovery_mail_outbox_kind_check;
ALTER TABLE identity.recovery_mail_outbox ADD CONSTRAINT recovery_mail_outbox_kind_check CHECK (kind IN ('CODE','CHANGED','SIGNUP','STAFF_INVITE'));
ALTER TABLE identity.recovery_mail_outbox ADD COLUMN provider_message_id varchar(100);
ALTER TABLE identity.recovery_mail_outbox ADD COLUMN delivery_status varchar(24) NOT NULL DEFAULT 'QUEUED';
CREATE INDEX idx_mail_provider_id ON identity.recovery_mail_outbox(provider_message_id);
CREATE TABLE identity.mail_delivery_events (
 event_id varchar(150) PRIMARY KEY,
 provider_message_id varchar(100) NOT NULL,
 delivery_status varchar(24) NOT NULL,
 occurred_at timestamptz NOT NULL,
 received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mail_events_provider ON identity.mail_delivery_events(provider_message_id,occurred_at DESC);

ALTER TABLE identity.refresh_tokens ADD COLUMN authenticated_at timestamptz;
ALTER TABLE identity.refresh_tokens ADD COLUMN last_interactive_at timestamptz;
UPDATE identity.refresh_tokens SET authenticated_at=created_at,last_interactive_at=created_at;
ALTER TABLE identity.refresh_tokens ALTER COLUMN authenticated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE identity.refresh_tokens ALTER COLUMN authenticated_at SET NOT NULL;
ALTER TABLE identity.refresh_tokens ALTER COLUMN last_interactive_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE identity.refresh_tokens ALTER COLUMN last_interactive_at SET NOT NULL;

CREATE TABLE identity.staff_passkey_grants (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES identity.users(id),
 token_hash varchar(64) NOT NULL UNIQUE,
 activation_hash varchar(64) NOT NULL,
 failed_attempts integer NOT NULL DEFAULT 0,
 kind varchar(12) NOT NULL CHECK (kind IN ('ENROLL','RECOVER','BOOTSTRAP')),
 issued_by uuid REFERENCES identity.users(id),
 identity_check_reference varchar(300) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at timestamptz NOT NULL,
 used_at timestamptz
);
CREATE INDEX idx_staff_grant_owner ON identity.staff_passkey_grants(user_id,created_at DESC);
ALTER TABLE identity.login_challenges ADD COLUMN staff_grant_id uuid REFERENCES identity.staff_passkey_grants(id);
-- Security challenges/grants/events are issued on demand, never seeded with usable credentials.
