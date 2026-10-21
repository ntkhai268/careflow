CREATE TABLE medical_service_prices (
 id uuid PRIMARY KEY,
 code varchar(50) NOT NULL,
 name varchar(150) NOT NULL,
 description varchar(500),
 amount bigint NOT NULL CHECK(amount BETWEEN 0 AND 1000000000000),
 currency varchar(3) NOT NULL CHECK(currency='VND'),
 version bigint NOT NULL CHECK(version>0),
 effective_at timestamptz NOT NULL,
 active boolean NOT NULL,
 estimated_minutes integer NOT NULL CHECK(estimated_minutes BETWEEN 1 AND 1440),
 created_by uuid,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(code,version)
);
CREATE INDEX idx_medical_service_price_effective ON medical_service_prices(code,version DESC,effective_at);
