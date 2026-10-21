# Patient — Uploaded health-record access repair

Branch: `feature/dangkhoii/patient-health-record-access`. Baseline `origin/develop` / `8bf77e2`; `git pull origin main` completed before coding. No develop implementation changes, commit or push.

## Scope and contract

Preserve the six routes, multipart parts, JSON fields and response DTO consumed by `frontend/patient-mobile/lib/services/health_record_service.dart`.

- Owner PATIENT may create/read/update/delete/download their uploaded records.
- Assigned DOCTOR may read/list/download; cannot alter patient uploads.
- STAFF/LAB_TECHNICIAN/ADMIN are not granted clinical upload access by role alone, matching Patient uploaded-record contract.
- File access checks both authenticated access to patientId and file -> record -> patient.
- A Health Records-specific JWT filter verifies signature, issuer, required expiry, subject and role even when the endpoint is called directly. A server-side request attribute contains the verified actor; client-supplied X-User headers do not establish identity for these routes.
- Existing PatientService verifies owner or doctor assignment. Assignment unavailable does not grant access.

## GitNexus Impact Assessment

GitNexus 1.6.12 CLI was used because MCP tools are not exposed. Fresh index created for this worktree before implementation.

Before modifying HealthRecordController and all six endpoint methods, upstream impact returned UNKNOWN with no resolved callers. HTTP entrypoints and some receiver types are not fully captured; this result was not treated as low risk. Source review confirmed all six Patient Mobile API operations use these routes.

Post-change detect_changes: controller/create/getById/update/delete/downloadFile, 25 affected execution flows, CRITICAL aggregate risk. The affected flows are expected uploaded-record and file operations. User was explicitly warned; no commit or integration performed. New filter/access service/tests/script are reviewed separately because the git-diff tool omits untracked files.

## Evidence

- Before fix, real direct GET of a seeded patient's health records without any token returned 200.
- Full Patient automated test suite: 55 tests pass, including 23 new HTTP-boundary regression cases.
- Live smoke using actual Identity-issued tokens: 48 HTTP cases pass.
- Covered: two profiles owned by the same user; multipart upload using mobile part names; owner list/detail/update/delete; real binary download; another account denied on every operation; no-token direct call denied despite spoofed headers; invalid direct token denied; patient/file mismatch denied.
- Denied updates/deletes leave original title and file bytes intact; owner deletion makes record/file return 404. Unit boundary cases verify no interaction with record/file/storage dependencies on denied requests.
- Assigned-doctor acceptance and assignment dependency failure are verified in controller tests. Additional live read acceptance, unassigned-doctor denial and forbidden doctor upload now pass against the real Appointment provider and its seeded doctor/patient assignment (5 HTTP cases, including 2 real logins). Run `python3 scripts/health-record-assigned-doctor-smoke.py --audit-db`; safe report `/tmp/careflow-health-record-assigned-doctor-live-results.json`. This does not claim the whole Appointment workflow is complete.
- Live script: `python3 scripts/patient-health-record-smoke.py --audit-db`. It creates synthetic audit accounts/profiles and removes the uploaded records/files. Account/profile fixtures remain in the disposable audit DB.
- Raw live case/status results: `/tmp/careflow-patient-health-record-live-results.json`. No token, password or clinical record contents in the report.

## Environment and limitations

Docker PostgreSQL is isolated on loopback port 15432; service overrides point to `careflow_patient` in this audit container, not the remote shared server. Uploads are in `/tmp/careflow-dangkhoii-audit/uploads`. Live services use host JDK 25 and compiled target 21. Test JVM requires the existing Byte Buddy experimental option on the host JDK.

This feature guards Health Records only. Remaining profile endpoints and other services still need the common authentication/caller hardening described in the system plan. JWT key/revocation/audience migration remains a separate feature; it is not claimed solved by this HMAC-compatible patch.

No entity, migration or new list/queue added; no DBML or initializer change needed. Existing seed tests remain passing. Existing create response has HTTP 201 with an envelope status of 200; this pre-existing consistency issue is documented for a separate response-contract correction.

## Missing-subject regression follow-up

A signed JWT without `sub` previously caused a null parsing error. The service now returns 401 before executing the request. Added `MissingSubjectAuthenticationTest`; final full service suite: 55 tests passing. Real HTTP case also passes in `/tmp/careflow-missing-subject-live-results.json`. The filter method impact lookup is UNKNOWN (framework method unresolved); its protected HTTP paths were manually reviewed. No edits to the existing policy service were needed.

Final refreshed tracked-file detect-changes returns LOW with zero indexed affected flows. Earlier broader HIGH/CRITICAL results remain documented; the graph has incomplete coverage and untracked new guards are excluded from this git-diff tool, so this final result is not a safety guarantee. No code was committed or merged.


## Active-session and booking continuation (2026-10-15)

Common's active-session SDK now guards every protected Patient /api endpoint, including profile APIs that previously trusted raw headers. Original Bearer and normalized identity/correlation are forwarded by PatientSecurityConfiguration to secured Appointment assignment checks; missing request context/Bearer is rejected. New GET /api/patients/{patientId}/booking-context permits PATIENT owner or ADMIN and returns only id/userId/fullName. It reuses PatientService's ownership check; no clinical/contact fields are returned. Additive CF-SVC-03 version 1.2 documents this dependency for Appointment's verified booking repair.

Full Patient suite now 61 tests passed (55 existing, three forwarding and three booking-view tests). HealthRecords 48 and actual assigned-doctor 5 HTTP cases pass after SDK deployment; the five assignment cases pass again after Appointment and Directory are secured. The Common distributed-session script covers Patient profile/uploads active, forged identity, revoked and authority outage statuses; Appointment's 30 live cases cover minimal booking data and cross-owner denial. Reports are case/status only, private disposable audit DB, no production policy/seed mutation.

Impact: new configuration/controller UNKNOWN before creation; existing PatientService policy is reused without edits. A test envelope assertion initially expected an empty data node, while the shared handler omits null data; corrected to assert no data is exposed (test-only impact UNKNOWN, no application caller). New files remain untracked by design and need manual diff review. Common and Appointment source dependencies are not merged; no service-wide or whole-system completion is claimed.
