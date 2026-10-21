-- Revocation must remain possible even if a second unowned reception record has the same card.
DROP INDEX IF EXISTS uk_unlinked_patient_id_card_number;
CREATE TABLE patient_link_authorizations (
    id uuid PRIMARY KEY, user_id uuid NOT NULL, token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL, used_at timestamptz, created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_patient_link_authorization_owner ON patient_link_authorizations(user_id,created_at);
CREATE TABLE patient_profile_links (
    id uuid PRIMARY KEY, patient_id uuid NOT NULL REFERENCES patients(id), owner_id uuid NOT NULL,
    room_id varchar(50) NOT NULL, requested_by uuid NOT NULL, identity_check_id uuid NOT NULL,
    reference varchar(80) NOT NULL, status varchar(30) NOT NULL,
    expires_at timestamptz NOT NULL, decided_by uuid, decided_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(status IN ('PENDING_OWNER','LINKED','DUPLICATE_REVIEW','REJECTED','EXPIRED','REVOKED'))
);
CREATE UNIQUE INDEX uq_profile_link_open ON patient_profile_links(patient_id) WHERE status IN ('PENDING_OWNER','LINKED','DUPLICATE_REVIEW');
CREATE INDEX idx_profile_link_owner ON patient_profile_links(owner_id,created_at);
CREATE INDEX idx_profile_link_room ON patient_profile_links(room_id,created_at);
CREATE TABLE patient_profile_link_audit (
    id uuid PRIMARY KEY, link_id uuid REFERENCES patient_profile_links(id), actor_id uuid NOT NULL,
    action varchar(40) NOT NULL, created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
