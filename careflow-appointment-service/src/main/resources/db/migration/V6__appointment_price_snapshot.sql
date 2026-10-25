ALTER TABLE appointments
 ADD COLUMN service_code varchar(50),
 ADD COLUMN service_name varchar(150),
 ADD COLUMN price_version bigint,
 ADD COLUMN quoted_amount bigint CHECK(quoted_amount>=0),
 ADD COLUMN quoted_currency varchar(3),
 ADD COLUMN price_effective_at timestamptz;
-- Historical appointments have unknown quotes; never backfill a made-up price.
