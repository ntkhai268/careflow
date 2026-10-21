ALTER TABLE identity.refresh_tokens ADD COLUMN authentication_method varchar(16) NOT NULL DEFAULT 'PASSWORD';
ALTER TABLE identity.refresh_tokens ADD CONSTRAINT refresh_authentication_method
 CHECK (authentication_method IN ('PASSWORD','PASSKEY','BIOMETRIC'));
-- Legacy sessions deliberately stay PASSWORD, including former question logins.
-- Production introspection rejects non-PATIENT sessions unless verified through passkey.
