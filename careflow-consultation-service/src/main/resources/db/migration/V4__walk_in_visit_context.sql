ALTER TABLE consultations ADD COLUMN queue_entry_id uuid;
CREATE UNIQUE INDEX uq_consultation_queue_entry ON consultations(queue_entry_id) WHERE queue_entry_id IS NOT NULL;
ALTER TABLE consultations ALTER COLUMN appointment_id DROP NOT NULL;
ALTER TABLE consultations ALTER COLUMN patient_name TYPE varchar(150);
