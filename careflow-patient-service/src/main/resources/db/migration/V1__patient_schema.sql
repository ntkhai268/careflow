-- Baseline schema for Patient Service
-- This must match the existing Hibernate-generated schema

CREATE TABLE IF NOT EXISTS patients (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL UNIQUE,
    full_name         VARCHAR(100) NOT NULL,
    date_of_birth     DATE,
    gender            VARCHAR(10),
    phone             VARCHAR(15),
    id_card_number    VARCHAR(20) UNIQUE,
    insurance_number  VARCHAR(20),
    occupation        VARCHAR(100),
    address           VARCHAR(500),
    avatar_url        VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_patient_user_id ON patients(user_id);
CREATE INDEX IF NOT EXISTS idx_patient_id_card ON patients(id_card_number);
