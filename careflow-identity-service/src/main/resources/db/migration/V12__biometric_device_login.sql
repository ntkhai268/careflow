CREATE TABLE identity.biometric_devices (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES identity.users(id),
 secret_hash varchar(64) NOT NULL,
 dpop_jkt varchar(43) NOT NULL,
 created_at timestamptz NOT NULL,
 last_used_at timestamptz,
 expires_at timestamptz NOT NULL,
 revoked_at timestamptz
);
CREATE UNIQUE INDEX idx_biometric_device_active ON identity.biometric_devices(user_id,dpop_jkt) WHERE revoked_at IS NULL;
CREATE INDEX idx_biometric_devices_user ON identity.biometric_devices(user_id);
