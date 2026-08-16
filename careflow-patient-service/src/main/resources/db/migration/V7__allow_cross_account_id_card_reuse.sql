-- A patient may recreate a profile on another account after losing access to
-- the original account. Keep the duplicate guard scoped to one account.
ALTER TABLE patients DROP CONSTRAINT IF EXISTS patients_id_card_number_key;
ALTER TABLE patients DROP CONSTRAINT IF EXISTS uk_patients_id_card_number;
ALTER TABLE patients DROP CONSTRAINT IF EXISTS idx_patient_id_card;
DROP INDEX IF EXISTS idx_patient_id_card;

CREATE INDEX IF NOT EXISTS idx_patient_id_card ON patients (id_card_number);
CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_user_id_card_number
    ON patients (user_id, id_card_number)
    WHERE id_card_number IS NOT NULL;
