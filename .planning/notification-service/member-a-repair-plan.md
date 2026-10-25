# Member A verified defects — implementation plan

Authorized 2026-10-15 by the user's request to plan and completely repair the four HIGH findings in gia_lap_mobile/docs/MEMBER-A-AUDIT.md. No commits, pushes, merges or shared remote DB writes. Existing develop changes and simulator datasets remain intact.

## GitNexus Impact Assessment

CLI used because GitNexus MCP is unavailable. Notification index created, Patient stale index refreshed, Queue index current. Main pulled in each feature worktree: already up to date. Full pre-edit reports: /tmp/careflow-member-a-repair-impacts.

| Symbol | Direct callers / scope | Risk |
|---|---|---|
| QueueManagementService.newEntry | 2 callers, 5 upstream symbols, 2 affected processes: manual intake and appointment entry creation | HIGH |
| Patient | 6 direct references, at least 65 upstream symbols and 117 flows; inheritance/dynamic boundaries incomplete | CRITICAL |
| JwtAuthenticator.authenticate | WebSocket CONNECT interceptor | LOW graph count; authentication critical operationally |
| WebSocketConfig / inbound registration | Spring framework callers absent from graph | UNKNOWN; verify actual broker/transport |
| NotificationService.consume/upsert/learnRecipientMapping | Rabbit listeners and recipient resolution | LOW |
| PatientRecipientProjection | 5 direct references / 13 upstream symbols | MEDIUM |
| Queue controller/intake DTO/service/repository | Intake endpoint, transaction, counter | See individual CLI reports; class coverage is conservative |

HIGH/CRITICAL impact warned before edits. User has explicitly authorized repairing these previously reported HIGH defects. No changes to scheduling weights, emergency policy or production seed-reset policy.

## Steps and acceptance

1. WebSocket: verify active session at CONNECT and before every outbound business MESSAGE; fail closed on Identity failure. Track actual transport sessions, close expired/revoked sessions on periodic verification and disconnect cleanup. Preserve personal destination and role checks. Verify real logout, logout-all/refresh/disable/role changes, expiry, and no message after revocation.
2. Recipient mapping: additive migration removing unique user_id, keep patient_id primary key and index user_id; atomic upsert avoids concurrent first-event collision and keeps event ordering. Explicit no-account Queue events have no mobile recipient and must not create failed notifications. Replay original matching DLQ events with original eventId after migration; dedup repeated delivery.
3. Queue counter: under existing configuration lock allocate above both durable counter and actual max sequence; preserve rows on restart and test missing/stale counter, retries and concurrent creates. No direct SQL status/counter patch as substitute for implementation.
4. Reception: STAFF/ADMIN command with actual Directory assignment and authoritative Patient owner lookup. Nullable userId means no account, never the creator. Record actor/method. Separate scoped minimal Patient reception creation endpoint for no-account patients with nullable-user migration and DBML. Invalid patient, forged owner, unrelated/inactive assignment and missing authority must fail without writes/events. Use existing duplicate-active and idempotency locks.
5. Build each feature module against the previously fixed Common library; compare deployed classes and apply migrations only in audit DB. Re-run original defects through emulator, real Rabbit and DB readback; retain all three existing sample accounts. Run scoped unit suites, browser and Identity regressions; record exact evidence and remaining limits. Refresh GitNexus and detect-changes for review, without committing.

## Isolation and deliverables

- Notification: feature/dangkhoii/notification-session-recipient; plan, implementation, migration, notification DBML/contract, tests, DLQ recovery utility.
- Queue: feature/dangkhoii/queue-access; counter/intake, contract/DBML/audit metadata if schema changes, tests.
- Patient: feature/dangkhoii/patient-health-record-access; minimal no-account reception API, migration/DBML/contract/tests.
- gia_lap_mobile: real regression harness and updated findings/report, no mock API.

eKYC provider integration, native FCM credentials, complete clinical/payment workflows and production hardening were not implemented by the old audit and are not silently included or claimed complete by these four repairs.

## Completion checkpoint

Implemented the four repairs and the additionally reproduced Gateway CORS PATCH omission. Scoped builds pass 189 unit tests (Notification 25, Queue 66, Patient 64, Gateway 34). Actual post-restart emulator/Rabbit/DB acceptance: 117 checks, zero failures; actual Identity outage/recovery: 11 checks; Identity lifecycle regression: 50 HTTP; three-phone Chrome regression: 7 checks. Two actual Queue JVMs share DB for concurrent/idempotency tests; token expiry is issued by an actual 5-second Identity probe, not locally forged. Notification V1–V3 and Patient V1–V8 migrate from empty PostgreSQL databases and upgrade the existing audit databases. One original verified DLQ event recovered exactly once; two non-allowlisted messages retained. Bytecode comparisons match all four deployed immutable jars. Probes stopped; primary services retained healthy.

Full report: `/Users/dangkhoii/gia_lap_mobile/docs/MEMBER-A-REPAIR.md`; machine evidence in that workspace's `.runtime/member-a-*.json`. GitNexus rebuilt and detect-changes reviewed without committing; tracked Queue/Patient scopes remain CRITICAL, and framework/untracked-file limits are explicit. Develop, user changes and original three simulator accounts remain preserved. Root-reactor integration after merging the separate Common/consumer branches and the out-of-scope readiness gaps remain unclaimed.
