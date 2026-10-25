ALTER TABLE notification.notifications ADD COLUMN patient_id UUID;
ALTER TABLE notification.device_installations ADD COLUMN session_id UUID;
ALTER TABLE notification.device_installations ADD COLUMN binding_id UUID;
ALTER TABLE notification.device_installations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notification.push_deliveries ADD COLUMN binding_id UUID;
ALTER TABLE notification.push_deliveries ADD COLUMN expires_at TIMESTAMPTZ NOT NULL DEFAULT now()+interval '5 minutes';
UPDATE notification.device_installations SET enabled=false WHERE session_id IS NULL;
UPDATE notification.push_deliveries SET status='DEAD',last_error='REQUIRES_SESSION_REBIND' WHERE binding_id IS NULL AND status IN ('PENDING','FAILED');
