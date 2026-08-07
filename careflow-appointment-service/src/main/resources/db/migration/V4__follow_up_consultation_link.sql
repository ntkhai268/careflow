ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS source_consultation_id UUID;

CREATE INDEX IF NOT EXISTS idx_appointment_source_consultation
    ON appointments (source_consultation_id);
