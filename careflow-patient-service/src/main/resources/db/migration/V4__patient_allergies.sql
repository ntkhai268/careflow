-- V4: Create patient_allergies table for structured allergy & safety guardrail
CREATE TABLE IF NOT EXISTS patient_allergies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    allergy_name VARCHAR(100) NOT NULL,
    allergy_group VARCHAR(100),
    severity VARCHAR(20) NOT NULL,
    reaction VARCHAR(255),
    confirmed_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_patient_allergies_patient ON patient_allergies(patient_id);
