# Mac check-in walkthrough — 2026-10-16

## Goal and acceptance

Make the existing real check-in flow discoverable from Mobile Emulator on Mac; no physical camera/GPS needed for the supported staff-assisted intake path. Keep patient QR/geofence check-in available as a separate genuine request path, with explicit test coordinates entered by the operator. No mock responses, status overrides, automatic login or shared privileged token.

1. Add a Mac E2E guide in the emulator with actual local Hospital Web links, role isolation instructions and the real booking → ticket → check-in → queue → call → consultation steps.
2. Add copyable signed QR text on Staff Check-in, with clipboard failure fallback; QR generation and patient check-in validators remain authoritative on backend.
3. Production build Hospital Web. Exercise staff login, QR copy and actual NOI-011 assisted check-in through browser; verify the same ticket in doctor ROOM-01 queue and patient view. Preserve current NOI-010 IN_PROGRESS consultation. Do not start another consultation while that doctor is busy.
4. Document current credentials, how to repeat on a new day, remaining duplicate-booking limitation and evidence. Refresh private Mac runtime backup; no stage/commit/push.

## GitNexus Impact Assessment

Index status: up-to-date before design (1000 files). Queried hospital QR/check-in flows and inspected StaffCheckinPage context. Upstream StaffCheckinPage: UNKNOWN, 0 graph callers/processes; Next.js route is the actual framework entry. Manual blast radius: only QR display/copy UI at /staff/checkin. Emulator changes are static HTML/CSS plus new top-level event callbacks; no existing fleet/session function changes. No backend state/API/authentication symbol is edited in this plan.

createAppointment impact was exploratory and ambiguous, so no modification is authorized by that incomplete result. Existing newly found duplicate booking inconsistency (same patient/department/day while prior queue active) is recorded as a separate open issue; this walkthrough uses an already valid real ticket and does not mask or repair it by DB manipulation.

## Completed

Static guide and QR copy deployed. 28 emulator Node tests and production Hospital Web build passed. 11 real browser checks passed; NOI-011 is CHECKED_IN and appears beside NOI-010 IN_PROGRESS in the actual assigned Doctor Web queue. Both states preserved. Full evidence, operating steps and limits: [mac-check-in-demo-results.md](mac-check-in-demo-results.md).
