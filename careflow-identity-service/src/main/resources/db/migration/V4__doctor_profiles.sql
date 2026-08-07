-- V4: Create doctor_profiles table (reconstructed from DB schema)
CREATE TABLE IF NOT EXISTS identity.doctor_profiles (
    id                  UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID                     NOT NULL,
    full_name           VARCHAR(200)             NOT NULL,
    license_number      VARCHAR(50),
    specialization      VARCHAR(100),
    department          VARCHAR(100),
    phone_number        VARCHAR(20),
    bio                 TEXT,
    avatar_url          VARCHAR(500),
    years_of_experience INTEGER,
    created_at          TIMESTAMPTZ              NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ              NOT NULL DEFAULT now(),

    CONSTRAINT doctor_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT doctor_profiles_user_id_key UNIQUE (user_id),
    CONSTRAINT doctor_profiles_license_number_key UNIQUE (license_number),
    CONSTRAINT doctor_profiles_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES identity.users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_doctor_profiles_user_id ON identity.doctor_profiles(user_id);
