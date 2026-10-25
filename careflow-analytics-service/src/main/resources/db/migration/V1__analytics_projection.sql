CREATE SCHEMA IF NOT EXISTS analytics;
CREATE TABLE analytics.snapshot_history (
 id BIGSERIAL PRIMARY KEY, source VARCHAR(20) NOT NULL, source_id UUID NOT NULL,
 synthetic BOOLEAN NOT NULL DEFAULT false, fingerprint CHAR(64) NOT NULL,
 payload JSONB NOT NULL, accepted BOOLEAN NOT NULL, observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(source,source_id,synthetic,fingerprint),
 CHECK(source IN ('APPOINTMENT','QUEUE','CONSULTATION'))
);
CREATE TABLE analytics.source_projection (
 source VARCHAR(20) NOT NULL, source_id UUID NOT NULL, synthetic BOOLEAN NOT NULL DEFAULT false,
 history_id BIGINT NOT NULL REFERENCES analytics.snapshot_history(id), payload JSONB NOT NULL,
 business_date DATE NOT NULL, department_id UUID, department_code VARCHAR(50), room_id VARCHAR(80),
 appointment_id UUID, initial_entry_id UUID, consultation_id UUID,
 source_version BIGINT NOT NULL, source_updated_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(source,source_id,synthetic)
);
CREATE INDEX idx_analytics_cohort ON analytics.source_projection(synthetic,business_date,department_id,room_id);
CREATE INDEX idx_analytics_initial ON analytics.source_projection(initial_entry_id);
CREATE INDEX idx_analytics_appointment ON analytics.source_projection(appointment_id);
CREATE TABLE analytics.event_receipts (
 source VARCHAR(20) NOT NULL, event_id UUID NOT NULL, source_id UUID NOT NULL,
 outcome VARCHAR(40) NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(source,event_id)
);
CREATE TABLE analytics.sync_state (
 source VARCHAR(20) PRIMARY KEY, cursor_id UUID, cycle_rows BIGINT NOT NULL DEFAULT 0,
 started_at TIMESTAMPTZ, last_success_at TIMESTAMPTZ, last_row_count BIGINT,
 status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', last_error_code VARCHAR(50),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO analytics.sync_state(source) VALUES ('APPOINTMENT'),('QUEUE'),('CONSULTATION');
CREATE TABLE analytics.operation_audit (
 id UUID PRIMARY KEY, actor_user_id UUID, operation VARCHAR(30) NOT NULL,
 affected_rows BIGINT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
