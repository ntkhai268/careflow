CREATE TABLE consultation_event_outbox (
 event_id uuid PRIMARY KEY, aggregate_id uuid NOT NULL, event_type varchar(80) NOT NULL,
 exchange_name varchar(100) NOT NULL, routing_key varchar(100) NOT NULL, payload jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz,
 attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_consultation_outbox_pending ON consultation_event_outbox(next_attempt_at,created_at) WHERE published_at IS NULL;
