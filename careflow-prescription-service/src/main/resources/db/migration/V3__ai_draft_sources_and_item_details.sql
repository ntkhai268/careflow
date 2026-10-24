ALTER TABLE prescription_items ADD COLUMN details JSONB;
CREATE TABLE prescription_ai_sources (
 job_id UUID NOT NULL,transcript_revision INTEGER NOT NULL CHECK(transcript_revision>0),
 prescription_id UUID NOT NULL UNIQUE REFERENCES prescriptions(id),consultation_id UUID NOT NULL,actor_id UUID NOT NULL,
 result_hash VARCHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(job_id,transcript_revision)
);
