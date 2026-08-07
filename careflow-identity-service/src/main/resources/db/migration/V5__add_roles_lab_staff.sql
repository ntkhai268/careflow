-- V5: Add LAB_TECHNICIAN and STAFF roles (reconstructed from DB schema)
-- The users.role CHECK constraint already includes LAB_TECHNICIAN in the DB.
-- This migration is a no-op since the constraint was created in V1 with all roles,
-- but is recorded here to satisfy Flyway schema history.
DO $$ BEGIN
    -- No-op: LAB_TECHNICIAN role already supported via CHECK constraint in V1
END $$;
