-- Human-reviewed security evidence, issued on demand; never seed fictitious confirmations.
-- Keep V15 historical provider metadata for audit, but no active adapter uses it.
CREATE TABLE identity.document_checks (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES identity.users(id),
 request_key uuid NOT NULL,
 payload_fingerprint varchar(64) NOT NULL,
 sealed_fields bytea,
 masked_document_number varchar(20) NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW'
   CHECK (status IN ('PENDING_REVIEW','CONFIRMED','REJECTED','WITHDRAWN','EXPIRED')),
 reason varchar(40),
 consent_version varchar(16) NOT NULL DEFAULT '2026-10-19',
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_at timestamptz,
 reviewed_by uuid REFERENCES identity.users(id),
 review_reference varchar(80),
 UNIQUE(user_id,request_key),
 CHECK ((status='PENDING_REVIEW' AND sealed_fields IS NOT NULL) OR (status<>'PENDING_REVIEW' AND sealed_fields IS NULL)),
 CHECK (status NOT IN ('CONFIRMED','REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reference IS NOT NULL))
);
CREATE INDEX idx_document_owner ON identity.document_checks(user_id,created_at DESC);
CREATE INDEX idx_document_pending ON identity.document_checks(created_at) WHERE status='PENDING_REVIEW';
