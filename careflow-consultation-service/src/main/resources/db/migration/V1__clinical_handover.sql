-- Safe for the existing Hibernate-managed schema and an empty database. Never resets clinical data.
CREATE TABLE IF NOT EXISTS consultations (
    id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    appointment_id UUID, patient_id UUID NOT NULL, doctor_id UUID NOT NULL,
    doctor_name VARCHAR(150), department_code VARCHAR(50), department_name VARCHAR(100),
    temperature NUMERIC(4,1), blood_pressure VARCHAR(20), heart_rate INTEGER, spo2 INTEGER,
    height NUMERIC(5,1), weight NUMERIC(5,1), symptoms TEXT, clinical_notes TEXT,
    icd10_code VARCHAR(10), icd10_name VARCHAR(500), diagnosis TEXT,
    status VARCHAR(20) NOT NULL, started_at TIMESTAMP, completed_at TIMESTAMP
);
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS responsible_doctor_id UUID;
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS responsible_doctor_name VARCHAR(150);
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_consultation_responsible ON consultations(responsible_doctor_id, status);
CREATE TABLE IF NOT EXISTS consultation_doctor_locks (doctor_id UUID PRIMARY KEY);
CREATE TABLE IF NOT EXISTS consultation_handovers (
    id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    consultation_id UUID NOT NULL REFERENCES consultations(id), patient_id UUID NOT NULL,
    from_doctor_id UUID NOT NULL, to_doctor_id UUID NOT NULL, initiated_by UUID NOT NULL,
    from_doctor_name VARCHAR(255), to_doctor_name VARCHAR(255),
    department_code VARCHAR(255) NOT NULL, room_id VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL, situation TEXT NOT NULL, background TEXT NOT NULL,
    assessment TEXT NOT NULL, recommendation TEXT NOT NULL,
    exceptional BOOLEAN NOT NULL DEFAULT FALSE, status VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, decided_by UUID, decided_at TIMESTAMPTZ, decision_note TEXT,
    CONSTRAINT handover_different_doctors CHECK (from_doctor_id <> to_doctor_id),
    CONSTRAINT handover_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_handover_pending ON consultation_handovers(consultation_id) WHERE status='PENDING';
CREATE INDEX IF NOT EXISTS idx_handover_receiver ON consultation_handovers(to_doctor_id, created_at);
CREATE TABLE IF NOT EXISTS handover_coordinators (
    doctor_id UUID PRIMARY KEY, active BOOLEAN NOT NULL DEFAULT FALSE,
    granted_by UUID NOT NULL, granted_at TIMESTAMPTZ NOT NULL, grant_reason TEXT NOT NULL,
    revoked_by UUID, revoked_at TIMESTAMPTZ, revoke_reason TEXT
);
INSERT INTO consultation_doctor_locks(doctor_id)
SELECT DISTINCT COALESCE(responsible_doctor_id,doctor_id) FROM consultations ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS handover_coordinator_audit (
    id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    doctor_id UUID NOT NULL, actor_id UUID NOT NULL, granted BOOLEAN NOT NULL, reason TEXT NOT NULL
);
