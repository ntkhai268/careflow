ALTER TABLE prescriptions ADD COLUMN follow_up_department varchar(50);
ALTER TABLE prescriptions ADD COLUMN follow_up_time_slot varchar(30);
ALTER TABLE prescriptions ADD COLUMN follow_up_status varchar(30) NOT NULL DEFAULT 'NOT_REQUESTED';
ALTER TABLE prescriptions ADD COLUMN follow_up_appointment_id uuid;
ALTER TABLE prescriptions ADD COLUMN follow_up_error_code varchar(50);
UPDATE prescriptions SET follow_up_status='NEEDS_SELECTION' WHERE follow_up_date IS NOT NULL;
ALTER TABLE prescriptions ADD CONSTRAINT ck_follow_up_status CHECK (follow_up_status IN ('NOT_REQUESTED','NEEDS_SELECTION','PENDING','FAILED','SCHEDULED'));
CREATE TABLE prescription_event_outbox (
 event_id uuid PRIMARY KEY, aggregate_id uuid NOT NULL, event_type varchar(80) NOT NULL,
 exchange_name varchar(100) NOT NULL, routing_key varchar(100) NOT NULL, payload jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz,
 attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_prescription_outbox_pending ON prescription_event_outbox(next_attempt_at,created_at) WHERE published_at IS NULL;
