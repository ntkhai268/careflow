ALTER TABLE queue.queue_entries
    ADD COLUMN scheduled_start_at TIMESTAMPTZ;

CREATE INDEX idx_queue_scheduled_appointment
    ON queue.queue_entries (queue_config_id, queue_date, status, scheduled_start_at)
    WHERE appointment_id IS NOT NULL;
