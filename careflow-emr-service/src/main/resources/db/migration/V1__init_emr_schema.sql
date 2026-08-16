-- Flyway Migration V1: Initialize EMR Master Health Records Schema
CREATE TABLE IF NOT EXISTS medical_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL UNIQUE,
    record_number VARCHAR(50) NOT NULL UNIQUE,
    blood_type VARCHAR(10),
    medical_history TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_emr_patient ON medical_records(patient_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_emr_record_number ON medical_records(record_number);
