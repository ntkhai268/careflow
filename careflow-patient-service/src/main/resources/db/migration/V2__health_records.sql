-- Health Records: patient-uploaded medical documents from previous visits

CREATE TABLE health_records (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID        NOT NULL REFERENCES patients(id) ON DELETE CASCADE,

    -- General info
    title               VARCHAR(255) NOT NULL,
    record_date         DATE         NOT NULL,
    facility_name       VARCHAR(255) NOT NULL,
    notes               TEXT,

    -- Health metrics
    blood_sugar         DECIMAL(5,2),
    blood_pressure      VARCHAR(10),
    height_cm           DECIMAL(5,1),
    weight_kg           DECIMAL(5,1),
    bmi                 DECIMAL(4,1),
    waist_cm            DECIMAL(5,1),
    blood_type          VARCHAR(5),
    pulse               INTEGER,
    temperature         DECIMAL(4,1),
    respiratory_rate    INTEGER,

    -- Family allergy history
    drug_allergy        VARCHAR(15),
    chemical_allergy    VARCHAR(15),
    food_allergy        VARCHAR(15),

    -- Family disease history
    heart_disease       VARCHAR(15),
    hypertension        VARCHAR(15),
    mental_illness      VARCHAR(15),
    cancer              VARCHAR(15),
    asthma              VARCHAR(15),
    epilepsy            VARCHAR(15),
    tuberculosis        VARCHAR(15),

    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_health_record_patient ON health_records(patient_id);
CREATE INDEX idx_health_record_date ON health_records(record_date);

CREATE TABLE health_record_files (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    health_record_id    UUID        NOT NULL REFERENCES health_records(id) ON DELETE CASCADE,
    file_name           VARCHAR(255) NOT NULL,
    stored_path         VARCHAR(500) NOT NULL,
    file_size           BIGINT,
    content_type        VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_health_record_file_record ON health_record_files(health_record_id);
