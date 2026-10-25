-- Older Hibernate update mode could create icd10code/icd10name beside the canonical columns.
-- Preserve legacy columns and stop if the clinical values disagree; never silently overwrite a diagnosis.
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS icd10_code varchar(10);
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS icd10_name varchar(500);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='consultations' AND column_name='icd10code') THEN
        IF EXISTS (SELECT 1 FROM consultations WHERE icd10_code IS NOT NULL AND icd10code IS NOT NULL AND icd10_code<>icd10code) THEN
            RAISE EXCEPTION 'Conflicting ICD code columns require clinical data review before migration';
        END IF;
        UPDATE consultations SET icd10_code=coalesce(icd10_code,icd10code);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='consultations' AND column_name='icd10name') THEN
        IF EXISTS (SELECT 1 FROM consultations WHERE icd10_name IS NOT NULL AND icd10name IS NOT NULL AND icd10_name<>icd10name) THEN
            RAISE EXCEPTION 'Conflicting ICD name columns require clinical data review before migration';
        END IF;
        UPDATE consultations SET icd10_name=coalesce(icd10_name,icd10name);
    END IF;
END $$;
