CREATE TABLE appointment_slot_locks (
    slot_key   VARCHAR(200) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
