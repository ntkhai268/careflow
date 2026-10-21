ALTER TABLE notification.patient_recipients
    DROP CONSTRAINT IF EXISTS patient_recipients_user_id_key;
CREATE INDEX IF NOT EXISTS idx_patient_recipients_user_id
    ON notification.patient_recipients(user_id);
