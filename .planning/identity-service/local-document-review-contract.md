# Local document extraction and original-document review — 2026-10-19

Current implementation replaces the retired VNPT adapter. Native source: `feature/dangkhoii/patient-mobile-ios-login`; backend/web: `feature/dangkhoii/security-integration`. This is a manual review workflow, not automatic eKYC or proof of document authenticity.

## Boundaries

- iPhone: ImagePicker camera → local Flutter platform channel → Apple Vision text/QR recognition → editable number/name/birth fields. Android: bundled ML Kit Latin recognizer, no remote OCR endpoint or dynamic model download. No selfie, face embedding or face comparison.
- Raw OCR/QR remains transient on device. Only documentNumber/fullName/birthDate and consent are sent as authenticated JSON, after explicit user review. No address/sex/image/provider token in request. Camera temporary file is deleted in finally after normal processing; process termination can leave OS-managed cache, so no claim of forensic memory/file erasure.
- OCR/QR is untrusted input, never a signature/authenticity check. Manual correction allowed. A pending request creates no patient-record link, user-role/session change or access to existing medical records.
- Historical provider requests/metadata remain historical. No new provider calls, no active provider Java classes/configuration credentials. Legacy `/api/auth/ekyc/status` and `/verify` return410, with no multipart arguments/provider invocation.

## API

Root `/api/auth/identity-document`; authenticated DPoP-bound session, no-store responses. Principal/roles read from verified Authentication, never user-supplied identity headers.

| Method/path | Authorization and contract |
|---|---|
| GET `/status` | PATIENT only; ready flag/method IN_PERSON/latest own masked summary. Historical VNPT results excluded. |
| POST `/requests` | PATIENT; UUID Idempotency-Key, JSON three fields + consent. Exact12-digit number, Unicode name2..100, valid ISO birthday1900..today. Returns PENDING_REVIEW only. |
| POST `/requests/{id}/withdraw` | Owning PATIENT, pending only; clears encrypted fields and sets WITHDRAWN. |
| GET `/pending?page=0` | STAFF/ADMIN;25 masked summaries/account names, bounded page number. Excludes expired requests. |
| GET `/requests/{id}` | STAFF/ADMIN; decrypts three fields only while pending and unexpired. Terminal/expired detail has fields=null. |
| POST `/requests/{id}/review` | STAFF/ADMIN, never self-review. Confirmation requires originalSeen/holderMatched/informationMatched all true and desk/audit reference3..80 allowed ASCII characters. Rejection has same reference requirement. |

Patient/doctor/lab cannot read or approve details; staff/admin cannot submit as a patient. Web pages `/staff/identity` and `/admin/identity` preserve existing role guards/403 AccessDenied, and API independently enforces roles. Browser detail clears when closed/hidden; no persistent browser storage of document fields.

## V17 state and storage

`identity.document_checks`: user/request UUIDs, HMAC fingerprint, AES256-GCM sealed JSON, masked number, state/consent version/timestamps/reviewer/reference. Dedicated32-byte base64 key from `IDENTITY_DOCUMENT_KEY_FILE`, outside Git; no reuse/rotation of email/Resend/outbox key. Row-ID/domain AAD prevents swapping ciphertext. Missing key disables submission; invalid configured key fails startup. Both instances need the same key.

- Per-owner PostgreSQL advisory transaction lock + unique(owner,key); same input replay returns same summary, changed input409. At most3 new requests/rolling24h. Pending/confirmed request blocks replacement. No provider quota involved.
- PENDING_REVIEW → CONFIRMED or REJECTED by staff; → WITHDRAWN by owner; → EXPIRED after7 days. Row locks/conditional updates allow only one review/withdrawal decision. Confirmation is human review evidence, not a provider VERIFIED result.
- All terminal transitions erase sealed fields. Expired requests become unreadable/unreviewable immediately; hourly cleanup physically clears overdue payloads. Metadata for non-confirmed records is pruned after90days; confirmed masked status/reviewer audit persists until a separately governed account/retention lifecycle.
- No photos/full document number/plain OCR in DB columns, logs or public summaries. Only pending authorized detail can return full fields. Clearing live rows is not deletion of historical backups; backup retention/access/key custody remains an operational requirement.
- No seeded declarations/consents/fake confirmations: security evidence is issued on demand, like email/device challenges, not master data. Automated tests create explicit synthetic rows and remove the live-test rows afterwards.

Operations/end-to-end instructions exist only in [canonical guide15](../../huong-dan-su-dung/15-doc-giay-to-va-doi-chieu.md). Future automatic document authenticity/PAD/NFC requires a separately validated design; this version does not simulate it.
