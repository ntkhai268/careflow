ALTER TABLE patients ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE patients ADD COLUMN reception_created_by_user_id UUID;
ALTER TABLE patients ADD COLUMN reception_room_id VARCHAR(50);
CREATE UNIQUE INDEX uk_unlinked_patient_id_card_number ON patients(id_card_number)
    WHERE user_id IS NULL AND id_card_number IS NOT NULL;
