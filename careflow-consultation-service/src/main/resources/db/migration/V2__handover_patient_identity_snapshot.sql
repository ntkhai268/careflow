ALTER TABLE consultations ADD COLUMN IF NOT EXISTS patient_name VARCHAR(100);
ALTER TABLE consultation_handovers ADD COLUMN IF NOT EXISTS patient_name VARCHAR(100);
