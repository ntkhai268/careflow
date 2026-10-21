CREATE TABLE identity.staff_account_provisioning (
    user_id uuid PRIMARY KEY REFERENCES identity.users(id),
    created_by uuid NOT NULL REFERENCES identity.users(id),
    request_id uuid NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    display_name varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (created_by, request_id)
);

CREATE TABLE identity.staff_account_audit (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES identity.users(id),
    user_id uuid NOT NULL REFERENCES identity.users(id),
    action varchar(24) NOT NULL CHECK (action IN ('CREATED', 'STATUS_CHANGED', 'ROLE_CHANGED')),
    previous_value varchar(20),
    new_value varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_staff_account_audit_user ON identity.staff_account_audit(user_id, created_at DESC);
-- Provisioning is issued by an authenticated administrator, never seeded with usable credentials.
