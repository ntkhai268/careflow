ALTER TABLE queue.queue_entries
    ADD COLUMN IF NOT EXISTS check_in_method VARCHAR(40),
    ADD COLUMN IF NOT EXISTS check_in_distance_meters DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS check_in_accuracy_meters DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS check_in_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS check_in_longitude DOUBLE PRECISION;

ALTER TABLE queue.queue_entries
    ADD CONSTRAINT ck_queue_check_in_distance_non_negative
        CHECK (check_in_distance_meters IS NULL OR check_in_distance_meters >= 0),
    ADD CONSTRAINT ck_queue_check_in_accuracy_positive
        CHECK (check_in_accuracy_meters IS NULL OR check_in_accuracy_meters > 0),
    ADD CONSTRAINT ck_queue_check_in_latitude_valid
        CHECK (check_in_latitude IS NULL OR check_in_latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_queue_check_in_longitude_valid
        CHECK (check_in_longitude IS NULL OR check_in_longitude BETWEEN -180 AND 180);
