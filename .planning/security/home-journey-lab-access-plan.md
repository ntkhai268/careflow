# Home journey: patient lab access

## Reproduction and contract

On 2026-10-16, the real patient `mobile_noi_c1d9dc` owns an IN_PROGRESS consultation. Appointment, ticket, queue, consultation and prescription reads succeed. Journey composition calls `GET /api/labs/orders/consultation/{id}`, which correctly returns 403: contract CF-SVC-10 reserves it for the assigned doctor. `GET /api/labs/orders/patient/{patientId}` succeeds with the patient's published results. This is a consumer/contract mismatch, not a network outage or MFA failure.

## GitNexus Impact Assessment

Index verified current; query and context inspected before planning. `_load` impact lookup is UNKNOWN (Dart method not resolved); enclosing `BackendJourneyRepository` impact is LOW, one direct caller (`journey_providers.dart`), 20 transitive impacted nodes, no graph-reported execution processes. Manual source inspection confirms bootstrap, notification-read refresh, Home and visit results share the loader. No backend authorization, schema, protocol or clinical state changes.

## Implementation and verification

1. Reproduce the current red Home card in the browser with the same real account.
2. Query the existing patient Lab API, retaining only results whose consultation and patient match the composed journey. Continue propagating authorization and availability errors; do not hide 403 or synthesize empty results after failures.
3. Regression cases: in-progress consultation with no published labs; published results isolated from another consultation/patient; 401/403/503 propagation.
4. Build the emulator, verify real Home restores the ongoing visit and uses the patient endpoint, and confirm the assigned-doctor endpoint still denies patients. Keep current appointment/queue/consultation data unchanged.

Known separate issue: a second active booking in the same department has no ticket; see `duplicate-active-booking-issue.md`. This fix does not cancel appointments or alter that booking policy.

Completed: release build deployed, 71 tests passed (one pre-existing skip), seven real browser/API checks passed, and all 14 health entries OK. See `home-journey-lab-access-results.md`.
