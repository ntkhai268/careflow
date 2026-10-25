CREATE TABLE identity.ekyc_verifications (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES identity.users(id),
 request_key uuid NOT NULL,
 payload_fingerprint varchar(64) NOT NULL,
 status varchar(24) NOT NULL CHECK (status IN ('PROCESSING','VERIFIED','REJECTED','REVIEW_REQUIRED','ERROR')),
 consent_version varchar(16) NOT NULL,
 masked_document_number varchar(20),
 reason varchar(40),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 processed_at timestamptz,
 UNIQUE(user_id,request_key)
);
CREATE INDEX idx_ekyc_owner_created ON identity.ekyc_verifications(user_id,created_at DESC);
CREATE TABLE identity.ekyc_request_budget (
 month_start date PRIMARY KEY,
 requests_used integer NOT NULL CHECK (requests_used BETWEEN 0 AND 100)
);
-- No fake verified identities are seeded. Rows are created only by authenticated requests.
