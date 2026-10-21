-- Supports both the existing Hibernate-managed database and a fresh test database.
CREATE TABLE IF NOT EXISTS drugs_dictionary (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 code varchar(50) NOT NULL UNIQUE, name varchar(200) NOT NULL, unit varchar(50), description text, active boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS prescriptions (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 consultation_id uuid NOT NULL, patient_id uuid NOT NULL, doctor_id uuid NOT NULL,
 diagnosis varchar(500), notes text, follow_up_date date, status varchar(30) NOT NULL,
 dispensing_service_point_id varchar(80), confirmed_at timestamptz, dispensed_at timestamptz,
 dispensed_by_user_id uuid, replaces_prescription_id uuid, cancellation_reason varchar(500),
 cancelled_at timestamptz, cancelled_by_user_id uuid
);
CREATE TABLE IF NOT EXISTS prescription_items (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 prescription_id uuid NOT NULL REFERENCES prescriptions(id), medicine_name varchar(255) NOT NULL,
 medicine_code varchar(50), unit varchar(50), dosage varchar(100), frequency varchar(100), timing varchar(100),
 duration integer, quantity integer, notes text
);
CREATE INDEX IF NOT EXISTS idx_prescription_patient ON prescriptions(patient_id);
CREATE INDEX IF NOT EXISTS idx_prescription_consultation ON prescriptions(consultation_id);
