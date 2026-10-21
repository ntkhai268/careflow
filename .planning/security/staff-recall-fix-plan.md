# Staff “Gọi lại” state routing fix — 2026-10-16

User correction: keep the existing “Gọi lại” button, fix behavior only; no bug icon or diagnostic feature.

## Reproduction

Real existing NOI-002 ticket checked in through Staff Web; clicking its existing “Gọi lại” button sends POST /api/queues/entries/{id}/recall and returns 409: “Không thể chuyển QueueEntry từ CHECKED_IN sang CALLED”. API /call accepts CHECKED_IN, while /recall correctly requires CALLED. Staff Web currently renders recall and miss for every active status. No backend transition change needed. Evidence: staff-recall-baseline.json.

A separate diagnostic call on NOI-011 to the correct /call endpoint returned 200 and advanced that real ticket to CALLED; preserve its history. NOI-010 clinical consultation remains IN_PROGRESS.

## GitNexus Impact Assessment

Index verified before design; flow query/context run. Precise upstream handleRecall (StaffCheckinPage.handleRecall@250:2): LOW, 1 direct caller, 1 mapped affected process (StaffCheckinPage; containing page participates in five flows), 2 modules. StaffCheckinPage framework entry: UNKNOWN, 0 graph callers; manual scope /staff/checkin. No auth, API transport, backend or schema symbols edited.

## Change and verification

1. Keep label “Gọi lại”; route CHECKED_IN/QUEUED to /call, CALLED to /recall. Disable outside callable states and after the existing three-call cap. Enable “Vắng mặt” only when CALLED. Refresh queue after action errors to avoid leaving stale status on screen.
2. Production-build and restart only the owned Hospital Web process.
3. Browser regression on the same real NOI-002: first click /call 200 → CALLED/attempt1; next click /recall 200 → attempt2; final allowed recall → attempt3, then button disabled. Verify active IN_PROGRESS case remains untouched and controls disabled for it. No reset, mocked API or invented state.
4. Document exact evidence, update index and preserve root user edits. No add/commit/push.

## Completion

Production build deployed, 8 browser regression checks passed using real APIs and the same previously failing NOI-002. Button label retained; no diagnostic icon added. See [staff-recall-fix-results.md](staff-recall-fix-results.md).
