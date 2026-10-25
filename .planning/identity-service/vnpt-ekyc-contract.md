# VNPT eKYC adapter — 2026-10-19

> Lịch sử, đã được thay thế: VNPT ngừng từ bản15 theo yêu cầu người dùng. Adapter đã bỏ, API cũ410. Contract hiện hành: [đọc trên máy và đối chiếu trực tiếp](local-document-review-contract.md). Các số liệu phía dưới chỉ đúng tại mốc kiểm tra cũ.


Scope: native Flutter camera capture → authenticated Identity REST → VNPT REST. No native VNPT SDK or NFC yet. Live update 2026-10-19: credentials are bound to an active Free project with all four required verification APIs; both test Identity instances enabled. Real upload/document-check negative control rejected a synthetic non-ID image (2 durable calls); replay spent zero further calls. Physical-device positive acceptance with real identity documents remains pending. See [live report](vnpt-ekyc-live-verification-2026-10-19.md).

## Contract

- `GET /api/auth/ekyc/status`: authenticated PATIENT only, account-scoped latest result and provider availability, `Cache-Control: no-store`.
- `POST /api/auth/ekyc/verify`: PATIENT only, UUID `Idempotency-Key`, multipart `front`, `back`, `selfie`, boolean `consent`. Up to 5MiB per image / 16MiB total. Decode real JPEG/PNG, minimum 240px each dimension, maximum 20M pixels, re-encode as JPEG without metadata. No images persisted by Identity.
- Normalized result: verificationId, status, provider VNPT, maskedDocumentNumber, reason enum, processedAt. Never return vendor response, hashes or full identity information. Existing single-image endpoint returns 410 after validation.
- `VERIFIED` requires document liveness on both sides with explicit non-fraud flags; OCR OK on both sides, 12-digit ID, valid birth date, no expiry/fake/tampering warnings, name/birth probabilities >=0.9; face liveness success/eyes open; provider MATCH and finite score 90..100. Missing/changed OCR contract requires review. These conservative application thresholds require calibration against actual tenant results.
- Verification does not bind an existing patient record, merge identities, grant medical-record access or change session authentication policy.

## Vendor transport

Official public documentation: https://ekyc.vnpt.vn/admin-dashboard/vi/documents/api, JS-rendered `DocsApiModule` (public `6-es2015.bb6c789cd005fc1d36e5.js`, Vietnamese document inspected 2026-10-19). Confirm against authenticated tenant docs before real acceptance.

- Fixed HTTPS origin `https://api.idg.vnpt.vn`; no client-controlled origin/path, redirects or automatic retries.
- Server-only headers `Authorization: Bearer …`, `Token-id`, `Token-key`. Private configuration may contain a raw token or a single case-insensitive Bearer prefix; normalize outer whitespace/prefix before dispatch. All three credentials must be nonempty printable ASCII without interior whitespace/control characters; invalid configuration disables availability before any quota is reserved.
- `/file-service/v1/addFile` multipart file/title/description → object.hash, three calls.
- `/ai/v1/card/liveness` `{img,client_session}` twice. Require explicit `liveness=success`, `face_swapping=false`, `fake_liveness=false`.
- `/ai/v1/ocr/id` `{img_front,img_back,client_session,type:-1,crop_param:"0,0",validate_postcode:false,token:""}` once.
- `/ai/v1/face/liveness` `{img,client_session,token:""}` once.
- `/ai/v1/face/compare` `{img_front,img_face,client_session,token:""}` once.
- Response must have HTTP200, message `IDG-00000000`, object JSON; bounded response 256KiB. Non-success, missing structure or credential errors become sanitized 503. TLS uses standard trust; local fixture constructor is package-private/test-only.

## Durable state and budget

V15 adds verification metadata and monthly quota; DBML updated. No seeded fake identities: these records are security evidence issued on demand, like reset-code/session records, not master/demo data.

- Uniqueness `(user_id,request_key)` and SHA256 canonical image fingerprint; per-account PostgreSQL advisory lock serializes creation. Replays return prior state; changed images with same key return409.
- Three new attempts per owner/rolling24h, persisted; replay does not consume an attempt. Interrupted PROCESSING older than10min renders ERROR, no vendor replay. No DB transaction held across network calls.
- Each outbound HTTP call consumes one committed monthly reservation BEFORE dispatch, including uploads/timeouts/errors. PostgreSQL upsert prevents races, cap <=100. Month boundary Asia/Ho_Chi_Minh. Budget does not reset on restart or compensate on failure. Configuration can reduce cap to remaining known tenant balance; shared tenant usage elsewhere is not measured.
- All-pass flow8 calls; max12 full flows within100 when otherwise unused. Vendor billing categories/entitlements remain tenant-specific. No face database registration/search, paid upgrade, NFC or unverified fallback.
- Disabled by default. Enabling requires all3 credentials and explicit Free entitlement confirmation. Private file lives outside Git. Resend unchanged.

## Verification boundaries

Automated: provider HTTP fixture, image validation, rejection/missing fields, no retry/redirect, durable budget race, request-key race, owner isolation, role denial, Flutter consent/step/status tests. Vendor liveness effectiveness, native camera, retention/consent terms and tenant billing are not established by these tests. User operations are maintained only in [canonical guide](../../huong-dan-su-dung/15-vnpt-ekyc.md).
