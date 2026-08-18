CREATE TABLE IF NOT EXISTS queue.hospital_check_in_configs (
    id UUID PRIMARY KEY,
    facility_code VARCHAR(50) NOT NULL UNIQUE,
    facility_name VARCHAR(160) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    allowed_radius_meters NUMERIC(10, 2) NOT NULL,
    max_accuracy_meters NUMERIC(10, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_hospital_check_in_latitude_valid CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_hospital_check_in_longitude_valid CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_hospital_check_in_radius_positive CHECK (allowed_radius_meters > 0),
    CONSTRAINT ck_hospital_check_in_accuracy_positive CHECK (max_accuracy_meters > 0)
);
