CREATE TABLE notification.device_installations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id VARCHAR(160) NOT NULL,
    registration_token VARCHAR(512) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    app_version VARCHAR(40),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_device_installation_user_device UNIQUE (user_id, device_id)
);

CREATE INDEX idx_device_installation_recipient
    ON notification.device_installations (user_id, enabled);

CREATE TABLE notification.push_deliveries (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notification.notifications(id) ON DELETE CASCADE,
    device_installation_id UUID NOT NULL REFERENCES notification.device_installations(id) ON DELETE CASCADE,
    registration_token VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT uk_push_delivery_notification_device UNIQUE (notification_id, device_installation_id)
);

CREATE INDEX idx_push_delivery_pending
    ON notification.push_deliveries (status, next_attempt_at, created_at);
