-- Keep the migrated patient schema aligned with the Patient entity.
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS allergy_notes VARCHAR(500),
    ADD COLUMN IF NOT EXISTS medical_history VARCHAR(1000);
