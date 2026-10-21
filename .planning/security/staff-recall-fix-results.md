# Fixed: “Gọi lại” rejects a CHECKED_IN ticket — 16/10/2026

User's final instruction: retain the existing **Gọi lại** label; fix the bug only, no bug icon or new diagnostic UI.

## Root cause and change

Staff Check-in rendered the recall button for every active queue state and always called `/recall`. Queue correctly accepts recall only from CALLED. A newly checked-in ticket therefore received 409 with “Không thể chuyển QueueEntry từ CHECKED_IN sang CALLED”. Reproduced in the real browser on NOI-002 after real staff-assisted check-in; evidence [staff-recall-baseline.json](staff-recall-baseline.json).

Only `frontend/doctor-web/src/app/(dashboard)/staff/checkin/page.tsx` business interaction changed:

- Same **Gọi lại** button sends `/call` for CHECKED_IN/QUEUED, `/recall` for CALLED.
- Disabled for other states and when the existing three-call cap is reached. Tooltip explains the cap.
- “Vắng mặt” enabled only for CALLED, matching its backend transition.
- Failed action refreshes the queue snapshot rather than keeping stale state.

No backend transition, authentication rule, API, schema or other button label changed. QR-copy UI from the previous task retained; no bug icon added.

## Verification and current real data

Production Hospital Web build passed and owned process at http://localhost:5173 restarted with that build. **8 real browser checks passed** via `node scripts/security/staff-recall-browser.mjs --audit-db`; [sanitized results](staff-recall-browser-results.json).

1. CHECKED_IN NOI-002 retains the label, first click calls `/call` → 200, CALLED, attempt 1.
2. Same button calls `/recall` → 200, attempt 2.
3. Same button calls `/recall` → 200, attempt 3.
4. Recall is disabled at cap 3; supported missed action available.
5. IN_PROGRESS NOI-010 cannot be recalled/marked missed through Staff UI; consultation remains untouched.
6. Real queue data reflects these actions; no reset, direct SQL or mocked runtime responses.
7. No failed API requests or uncaught JavaScript errors in the fixed browser journey.

The script consumes the three permitted calls on an already CHECKED_IN ticket (default NOI-002); it intentionally cannot be repeated on that now-CALLED ticket. To repeat, check in another valid real ticket and pass `CAREFLOW_RECALL_TICKET`. It never creates, resets or rewinds queue history.

During diagnosis, the correct `/call` endpoint on NOI-011 was also exercised successfully: it is now CALLED with attempt 1 and can be recalled through the repaired UI. NOI-010 remains IN_PROGRESS. Existing demo patients and history preserved.

## Scope

GitNexus upstream impact before edits: handleRecall LOW, 1 direct caller/1 mapped affected process; framework page UNKNOWN/0 graph callers, manually reviewed /staff/checkin entry. Next.js local client-component guidance and Queue contract/state flow reviewed. Root develop user edits preserved. Code runs from `.worktrees/security-integration`, branch `feature/dangkhoii/security-integration`. No stage/commit/push.

Reload Staff Web with ⌘R to load the repaired client bundle. Full system and downstream clinical completion were not retested for this frontend-only fix.
