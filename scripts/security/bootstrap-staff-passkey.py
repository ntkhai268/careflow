#!/usr/bin/env python3
"""Operator-only first ADMIN enrollment. Requires psql PG* environment and cryptography.
Usage and the two-channel delivery procedure live in huong-dan-su-dung/16-bao-mat-dot-2.md.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import stat
import subprocess
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--user-id', type=uuid.UUID, required=True)
    parser.add_argument('--identity-reference', required=True)
    parser.add_argument('--outbox-key-file', type=Path, required=True)
    parser.add_argument('--activation-file', type=Path, required=True)
    args = parser.parse_args()
    if not 8 <= len(args.identity_reference.strip()) <= 300:
        parser.error('Identity reference must contain 8–300 characters.')
    key_file = args.outbox_key_file.resolve(strict=True)
    if stat.S_IMODE(key_file.stat().st_mode) & 0o077 or key_file.stat().st_uid != os.getuid():
        parser.error('The existing outbox key must be owned by this operator with mode 0600.')
    key = base64.b64decode(key_file.read_text().strip(), validate=True)
    if len(key) != 32:
        parser.error('Invalid existing AES-256 outbox key.')
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    def sql_literal(value):
        # Explicit standard_conforming_strings prevents backslash interpretation.
        return "'" + str(value).replace("'", "''") + "'"

    def execute(statement):
        result = subprocess.run(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1'],
                                input='SET standard_conforming_strings=on;\n' + statement,
                                text=True, capture_output=True)
        if result.returncode:
            # psql diagnostics may include recipient information or SQL; keep them private.
            raise RuntimeError('Bootstrap refused or database unavailable; no credentials printed.')
        return result.stdout.strip()

    owner = str(args.user_id)
    email = execute("SELECT email FROM identity.users WHERE id=" + sql_literal(owner) + " AND role='ADMIN' AND status='ACTIVE';")
    if not email or '\n' in email:
        parser.error('Select an existing active ADMIN account.')
    grant, outbox = str(uuid.uuid4()), str(uuid.uuid4())
    email_code, activation = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    digest = lambda text: hashlib.sha256(text.encode()).hexdigest()
    nonce = secrets.token_bytes(12)
    payload = nonce + AESGCM(key).encrypt(nonce, json.dumps({'email': email, 'code': grant + '.' + email_code}).encode(), outbox.encode('ascii'))
    statement = f"""
BEGIN;
-- Serialize bootstrap invocations; never remove existing passkeys or sessions.
SELECT pg_advisory_xact_lock(741963852);
LOCK TABLE identity.passkeys IN SHARE ROW EXCLUSIVE MODE;
SELECT id FROM identity.users WHERE id={sql_literal(owner)} FOR UPDATE;
DO $check$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM identity.users WHERE id={sql_literal(owner)} AND role='ADMIN' AND status='ACTIVE' AND email={sql_literal(email)})
 OR EXISTS(SELECT 1 FROM identity.passkeys p JOIN identity.users u ON u.id=p.user_id WHERE u.role='ADMIN' AND u.status='ACTIVE')
 THEN RAISE EXCEPTION 'Bootstrap requires first active ADMIN passkey'; END IF;
END $check$;
UPDATE identity.staff_passkey_grants SET used_at=now() WHERE user_id={sql_literal(owner)} AND used_at IS NULL;
INSERT INTO identity.staff_passkey_grants(id,user_id,token_hash,activation_hash,kind,identity_check_reference,expires_at)
VALUES ({sql_literal(grant)},{sql_literal(owner)},{sql_literal(digest(email_code))},{sql_literal(digest(activation))},'BOOTSTRAP',{sql_literal(args.identity_reference.strip())},now()+interval '15 minutes');
INSERT INTO identity.recovery_mail_outbox(id,challenge_id,kind,payload,expires_at)
VALUES ({sql_literal(outbox)},{sql_literal(grant)},'STAFF_INVITE',decode('{payload.hex()}','hex'),now()+interval '15 minutes');
INSERT INTO identity.security_events(id,user_id,event_type) VALUES ('{uuid.uuid4()}',{sql_literal(owner)},'STAFF_PASSKEY_BOOTSTRAP');
COMMIT;
"""
    # Reserve a private file before changing the database; refuse overwrites/symlinks.
    fd = os.open(args.activation_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as handle:
        try:
            execute(statement)
            json.dump({'grantId': grant, 'activationCode': activation, 'expiresInSeconds': 900}, handle)
            handle.write('\n')
            handle.flush()
            os.fsync(handle.fileno())
        except Exception:
            args.activation_file.unlink(missing_ok=True)
            raise
    print('Bootstrap grant queued. Activation stored in the private file; expires after 15 minutes.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Never print SQL, OTP, activation codes, key material or connection strings.
        raise SystemExit(type(error).__name__ + ': bootstrap failed; verify private operator configuration.')
