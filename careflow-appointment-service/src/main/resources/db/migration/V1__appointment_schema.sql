CREATE TABLE appointments (
    id               UUID         PRIMARY KEY,
    patient_id       UUID         NOT NULL,
    patient_name     VARCHAR(100),
    department       VARCHAR(30)  NOT NULL,
    doctor_id        UUID,
    doctor_name      VARCHAR(100),
    appointment_date DATE         NOT NULL,
    time_slot        VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reason           VARCHAR(500),
    notes            VARCHAR(1000),
    queue_number     VARCHAR(20),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_appointment_patient   ON appointments (patient_id);
CREATE INDEX idx_appointment_dept_date ON appointments (department, appointment_date);
CREATE INDEX idx_appointment_status    ON appointments (status);
