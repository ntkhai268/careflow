CREATE TABLE IF NOT EXISTS lab_orders (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    ordered_by_doctor_id UUID NOT NULL,
    department_id UUID,
    clinical_note TEXT,
    status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lab_order_status CHECK (
        status IN ('ORDERED','PAYMENT_PENDING','QUEUED','CALLED','IN_PROGRESS',
                   'RESULT_AVAILABLE','REVIEWED','CANCELLED')
    ),
    CONSTRAINT ck_lab_payment_status CHECK (
        payment_status IN ('NOT_REQUIRED','PENDING','PAID_ONLINE_MOCK','PAID_CASH',
                           'COVERED_BY_INSURANCE')
    )
);

CREATE TABLE IF NOT EXISTS lab_order_items (
    id UUID PRIMARY KEY,
    lab_order_id UUID NOT NULL REFERENCES lab_orders(id) ON DELETE CASCADE,
    service_code VARCHAR(50) NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_point_id VARCHAR(80) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL,
    result_value VARCHAR(255),
    result_unit VARCHAR(50),
    reference_range VARCHAR(100),
    result_flag VARCHAR(30),
    comment TEXT,
    performed_by_staff_id UUID,
    performed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lab_order_item_status CHECK (status IN ('ORDERED','COMPLETED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_lab_order_consultation ON lab_orders(consultation_id);
CREATE INDEX IF NOT EXISTS idx_lab_order_patient ON lab_orders(patient_id);
CREATE INDEX IF NOT EXISTS idx_lab_order_status ON lab_orders(status);
CREATE INDEX IF NOT EXISTS idx_lab_item_order ON lab_order_items(lab_order_id);
CREATE INDEX IF NOT EXISTS idx_lab_item_service_point ON lab_order_items(service_point_id);
