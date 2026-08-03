CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.patient_recipients (
    patient_id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notification.notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    source_event_id UUID NOT NULL,
    source_event_type VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL CHECK (notification_type IN (
        'APPOINTMENT_CONFIRMED', 'VISIT_TICKET_ISSUED', 'CHECK_IN_SUCCESS', 'QUEUE_NEAR_TURN',
        'QUEUE_CALLED', 'QUEUE_MISSED', 'LAB_ORDER_CREATED', 'LAB_READY',
        'LAB_RESULT_AVAILABLE', 'RETURN_FOR_REVIEW', 'PRESCRIPTION_AVAILABLE',
        'FOLLOW_UP_SCHEDULED'
    )),
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    action_type VARCHAR(50),
    resource_id VARCHAR(100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'READ', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    CONSTRAINT uk_notification_source_recipient_type
        UNIQUE (source_event_id, recipient_user_id, notification_type)
);

CREATE INDEX idx_notification_inbox
    ON notification.notifications (recipient_user_id, created_at DESC);
CREATE INDEX idx_notification_unread
    ON notification.notifications (recipient_user_id, status, created_at DESC);

CREATE TABLE notification.outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    exchange_name VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_outbox_pending
    ON notification.outbox_events (status, next_attempt_at, created_at);
