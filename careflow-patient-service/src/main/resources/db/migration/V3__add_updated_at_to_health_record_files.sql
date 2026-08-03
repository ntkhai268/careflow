-- Add missing updated_at column to health_record_files table
-- BaseEntity requires both created_at and updated_at

ALTER TABLE health_record_files
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
