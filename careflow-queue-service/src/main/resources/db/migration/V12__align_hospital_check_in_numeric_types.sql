ALTER TABLE queue.hospital_check_in_configs
    ALTER COLUMN latitude TYPE DOUBLE PRECISION USING latitude::double precision,
    ALTER COLUMN longitude TYPE DOUBLE PRECISION USING longitude::double precision,
    ALTER COLUMN allowed_radius_meters TYPE DOUBLE PRECISION USING allowed_radius_meters::double precision,
    ALTER COLUMN max_accuracy_meters TYPE DOUBLE PRECISION USING max_accuracy_meters::double precision;
