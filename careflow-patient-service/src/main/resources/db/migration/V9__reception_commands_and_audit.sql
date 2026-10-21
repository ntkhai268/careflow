CREATE TABLE patient_reception_commands (
    actor_id uuid NOT NULL,
    request_id uuid NOT NULL,
    fingerprint varchar(64) NOT NULL,
    patient_id uuid NOT NULL REFERENCES patients(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(actor_id,request_id)
);
CREATE TABLE patient_reception_audit (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL,
    room_id varchar(50) NOT NULL,
    action varchar(32) NOT NULL,
    patient_id uuid REFERENCES patients(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reception_audit_actor ON patient_reception_audit(actor_id,created_at);
CREATE TABLE patient_reception_search_rate (
    actor_id uuid NOT NULL,
    window_at timestamptz NOT NULL,
    attempts integer NOT NULL,
    PRIMARY KEY(actor_id,window_at)
);
