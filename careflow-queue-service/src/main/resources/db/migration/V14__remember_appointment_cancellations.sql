-- Cancellation can arrive before confirmation or be replayed after a restart.
-- Appointment IDs are immutable; a new booking receives a new ID.
CREATE TABLE queue.appointment_cancellations (
    appointment_id uuid PRIMARY KEY,
    cancellation_event_id uuid,
    cancelled_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO queue.appointment_cancellations(appointment_id,cancelled_at)
SELECT appointment_id,COALESCE(cancelled_at,CURRENT_TIMESTAMP)
FROM queue.queue_entries
WHERE appointment_id IS NOT NULL AND status='CANCELLED'
ON CONFLICT(appointment_id) DO NOTHING;
