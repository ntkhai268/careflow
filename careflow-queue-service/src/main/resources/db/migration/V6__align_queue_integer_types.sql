ALTER TABLE queue.queue_configs
    ALTER COLUMN priority_ratio_n TYPE INTEGER,
    ALTER COLUMN normal_ratio_m TYPE INTEGER,
    ALTER COLUMN avg_consultation_minutes TYPE INTEGER,
    ALTER COLUMN near_turn_threshold TYPE INTEGER,
    ALTER COLUMN served_in_phase TYPE INTEGER;

ALTER TABLE queue.queue_entries
    ALTER COLUMN call_attempts TYPE INTEGER,
    ALTER COLUMN missed_count TYPE INTEGER;
