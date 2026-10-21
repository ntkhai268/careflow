# Booking → ticket → doctor E2E repair — 2026-10-16

User reported a real booking returning 201, ticket lookup 404, Consultation 401, refresh 200 and subsequent repeated 401, with no ticket in mobile and no appointment on Internal Medicine Doctor Web. Fix within the existing authorized security integration; preserve user-created appointment and root develop edits. No stage/commit/push, remote DB writes or mocked runtime responses.

## Confirmed baseline

Real appointment 52106adc-8f8c-4361-a621-1323d946c4db is CONFIRMED for NGOAI / ROOM-03, doctor d0000004…, 2026-10-16 10:00–10:30. Its real Queue ticket exists (NGO-001, TICKET_ISSUED). It must not appear in a ROOM-01 doctor's list. A fresh valid patient session receives Appointment 200, Ticket 200, Consultation 401 and Identity /me 200. Consultation, EMR, Prescription and HealthRecord still register legacy HMAC guards in addition to Common's active-session guard; real Identity now signs RSA. The first ticket 404 can be normal outbox/event propagation, but must not be presented as a missing appointment. Mobile refresh currently retries and clears credentials even when refresh succeeds but resource authentication remains broken.

## GitNexus Impact Assessment

Fresh integrated index, flow query/context and upstream impacts run before planning. ApiService HIGH, 15 direct callers / 58 impacted symbols: auth refresh affects all mobile API consumers; user already authorized HIGH/CRITICAL security repairs, warned again before edits. QueueService MEDIUM, 5 direct callers. Appointment detail _loadAppointment LOW, 1 direct caller; error rendering part of the same screen will be checked. Four framework authentication filter classes UNKNOWN/0 graph callers: manual radius is every protected endpoint in those four services and seven dependent test classes. DashboardGeneralPage UNKNOWN: React framework entry; manual scope clinical-context/queue display. Test classes UNKNOWN, no runtime callers.

## Changes and acceptance

1. Remove duplicate HMAC authentication components; Common online Identity/session/DPoP/mTLS validation remains mandatory. Migrate authorization unit fixtures to explicit session-authority stubs. Build CLEAN so deleted classes cannot remain packaged. Test positive RSA API calls and revoked/invalid/bound-token denials on all four real services.
2. Mobile single-flight refresh with stale-request detection; do not clear valid new tokens because a resource retry failed; keep credentials on transient refresh outage; do not refresh MFA business 401. Regenerate proof on retry. Add focused concurrent refresh/error tests.
3. Bounded ticket-404 retry with truthful pending/error states; show actual appointment load errors. Port user's existing clinical-context room selection into integrated Doctor Web without writing root user files; propagate load failures instead of silently showing an empty queue.
4. Actual browser/API E2E: real patient books Internal Medicine, event creates ticket, mobile displays it, pre-check-in excluded from active queue, real QR/geofence check-in, assigned Internal Medicine doctor sees/calls/starts visit, patient reads started consultation/health/EMR/prescription resources with original role rules. Verify wrong room, ownership, revoked sessions and idempotency. No automatic state manipulation to fake completion. Preserve user's NGOAI booking.
5. Deploy reviewed builds to owned local services, refresh Mac private snapshot, document exact flow and evidence. Do not call booking equivalent to being checked in or a consultation being started.

Additional confirmed start-path repair: original Consultation create swallowed Appointment validation failures and attempted an unsupported IN_PROGRESS appointment transition. Upstream impact for the precise create overload: LOW, 1 direct caller/1 mapped process. Add assignment-checked Queue authority before creating/reentering a consultation and require CALLED/IN_PROGRESS; handle verified reception walk-ins without bypassing Queue. Common principal attribute adapter preserves HealthRecordActor (Common filter LOW, one graph caller; manual radius all servlet services). Added controller/service authority-negative tests and real positive RSA reads. AuditClient proof helper LOW, 3 mapped callers; strip URI query/fragment per DPoP canonical URI for test requests.


## Completion

Cold/deep-link scope loading repaired; original screenshot account now renders NGO-001 in a real browser without 401. One verified orphan test consultation was cancelled through the authorized doctor API, preserving history; the real NOI-010 demo consultation remains IN_PROGRESS. Ownership error mapping impact: CRITICAL, 3 direct callers/6 mapped flows; warning communicated before repair under existing authorization. Distinguish access denial 403 from authority outage 503.

Final: 610 backend tests, 25 Flutter tests, 31 real RSA-consumer checks passed. Latest browser continuation on the same real case: 14 checks, no spurious 401/JavaScript errors. Booking, check-in, call and start were exercised through real interfaces over resumed stages. Four deployed services checked against reviewed bytecode. All 14 runtime health entries healthy; private snapshot and DB backup refreshed. No stage/commit/push. Full evidence and limits: [booking-ticket-doctor-e2e-results.md](booking-ticket-doctor-e2e-results.md).
