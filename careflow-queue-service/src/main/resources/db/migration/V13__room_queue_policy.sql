ALTER TABLE queue.queue_configs DROP CONSTRAINT IF EXISTS queue_configs_department_id_key;
ALTER TABLE queue.queue_configs ADD COLUMN policy_version bigint NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uq_queue_config_room ON queue.queue_configs(room_code) WHERE room_code IS NOT NULL;
CREATE TABLE queue.room_policy_audit (
    id uuid PRIMARY KEY,
    queue_config_id uuid NOT NULL REFERENCES queue.queue_configs(id),
    actor_id uuid NOT NULL,
    policy_version bigint NOT NULL,
    settings jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(queue_config_id,policy_version)
);
-- Existing initial-phase commands used the department as scope while there was one room per department.
UPDATE queue.idempotency_records r SET scope_id=c.id FROM queue.queue_configs c
WHERE r.scope_id=c.department_id AND r.command_name IN ('MANUAL_INTAKE','CALL_NEXT');

ALTER TABLE queue.queue_entries ADD COLUMN patient_name_snapshot varchar(150);

ALTER TABLE queue.queue_entries ADD COLUMN service_code varchar(50);

-- Multiple future bookings may coexist; only one initial consultation can be checked in/served per department/day.
DROP INDEX queue.uk_queue_active_initial_consultation;
CREATE UNIQUE INDEX uk_queue_active_initial_consultation
    ON queue.queue_entries(patient_id,department_id,queue_date)
    WHERE queue_type='CONSULTATION' AND consultation_phase='INITIAL'
      AND status IN ('CHECKED_IN','CALLED','IN_PROGRESS','MISSED');
