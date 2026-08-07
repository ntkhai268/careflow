-- V7: Drop unused doctor_profiles table and title column from identity service
DROP TABLE IF EXISTS identity.doctor_profiles CASCADE;

ALTER TABLE identity.users DROP COLUMN IF EXISTS title;
