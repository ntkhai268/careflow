-- One account may manage profiles for the account holder and family members.
-- Keep ownership on user_id; remove only the obsolete one-profile constraint.
ALTER TABLE patients DROP CONSTRAINT IF EXISTS patients_user_id_key;
-- Some environments created the unique constraint using the index name.
ALTER TABLE patients DROP CONSTRAINT IF EXISTS idx_patient_user_id;
DROP INDEX IF EXISTS idx_patient_user_id;
CREATE INDEX IF NOT EXISTS idx_patient_user_id ON patients(user_id);
