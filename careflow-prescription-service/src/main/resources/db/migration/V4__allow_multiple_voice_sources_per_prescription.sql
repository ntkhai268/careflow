ALTER TABLE prescription_ai_sources
    DROP CONSTRAINT prescription_ai_sources_prescription_id_key;

CREATE INDEX idx_prescription_ai_sources_prescription
    ON prescription_ai_sources(prescription_id);
