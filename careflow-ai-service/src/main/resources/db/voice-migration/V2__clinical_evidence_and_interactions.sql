CREATE TABLE ai_guideline_sources (
 id UUID PRIMARY KEY,title VARCHAR(200) NOT NULL,revision VARCHAR(80) NOT NULL,department_code VARCHAR(50) NOT NULL,
 source_url VARCHAR(1000) NOT NULL,content TEXT NOT NULL,content_hash VARCHAR(64) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 synthetic BOOLEAN NOT NULL DEFAULT false,imported_by UUID NOT NULL,reviewer_id UUID,reviewed_at TIMESTAMP WITH TIME ZONE,created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
 UNIQUE(department_code,title,revision),CHECK(status IN ('DRAFT','APPROVED','RETIRED')),
 CHECK(status<>'APPROVED' OR (reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL AND reviewer_id<>imported_by))
);
CREATE TABLE ai_clinical_interactions (
 id UUID PRIMARY KEY,actor_id UUID NOT NULL,consultation_id UUID NOT NULL,parent_id UUID,
 context_hash VARCHAR(64) NOT NULL,content_cipher TEXT NOT NULL,engine_id VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ai_interaction_owner ON ai_clinical_interactions(actor_id,consultation_id,created_at DESC);
CREATE INDEX idx_ai_interaction_expiry ON ai_clinical_interactions(expires_at);
