-- Queue integration is versioned after the server's existing V2 migration.
ALTER TABLE appointments
    ADD COLUMN owner_user_id UUID,
    ADD COLUMN department_id UUID,
    ADD COLUMN room_id VARCHAR(50),
    ADD COLUMN room_display_name VARCHAR(150);

UPDATE appointments
SET owner_user_id = patient_id,
    department_id = CASE department
        WHEN 'NOI_TONG_QUAT' THEN '10000000-0000-0000-0000-000000000001'::uuid
        WHEN 'NHI' THEN '10000000-0000-0000-0000-000000000002'::uuid
        WHEN 'NGOAI' THEN '10000000-0000-0000-0000-000000000003'::uuid
        WHEN 'SAN' THEN '10000000-0000-0000-0000-000000000004'::uuid
        WHEN 'MAT' THEN '10000000-0000-0000-0000-000000000005'::uuid
        WHEN 'TAI_MUI_HONG' THEN '10000000-0000-0000-0000-000000000006'::uuid
        WHEN 'RANG_HAM_MAT' THEN '10000000-0000-0000-0000-000000000007'::uuid
        WHEN 'DA_LIEU' THEN '10000000-0000-0000-0000-000000000008'::uuid
        WHEN 'THAN_KINH' THEN '10000000-0000-0000-0000-000000000009'::uuid
        WHEN 'TIM_MACH' THEN '10000000-0000-0000-0000-000000000010'::uuid
        ELSE '10000000-0000-0000-0000-000000000011'::uuid
    END,
    room_id = CASE WHEN department = 'THAN_KINH' THEN 'ROOM-21'
        ELSE 'ROOM-' || LPAD((CASE department
            WHEN 'NOI_TONG_QUAT' THEN 1 WHEN 'NHI' THEN 2 WHEN 'NGOAI' THEN 3
            WHEN 'SAN' THEN 4 WHEN 'MAT' THEN 5 WHEN 'TAI_MUI_HONG' THEN 6
            WHEN 'RANG_HAM_MAT' THEN 7 WHEN 'DA_LIEU' THEN 8 WHEN 'TIM_MACH' THEN 10
            ELSE 11 END)::text, 2, '0') END,
    room_display_name = 'Phòng khám ' || department
WHERE owner_user_id IS NULL;

ALTER TABLE appointments
    ALTER COLUMN owner_user_id SET NOT NULL,
    ALTER COLUMN department_id SET NOT NULL,
    ALTER COLUMN room_id SET NOT NULL,
    ALTER COLUMN room_display_name SET NOT NULL;

CREATE INDEX idx_appointment_owner ON appointments(owner_user_id);

CREATE TABLE appointment_outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_appointment_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED','DEAD'))
);
CREATE INDEX idx_appointment_outbox_pending
    ON appointment_outbox_events(status, next_attempt_at, created_at);
