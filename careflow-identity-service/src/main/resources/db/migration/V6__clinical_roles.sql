-- Clinical roles are versioned after the shared server's existing V3-V5 migrations.
ALTER TABLE identity.users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE identity.users ADD CONSTRAINT users_role_check
    CHECK (role IN ('PATIENT', 'DOCTOR', 'STAFF', 'LAB_TECHNICIAN', 'ADMIN'));
