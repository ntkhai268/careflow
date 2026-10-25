# Home journey Lab access — verified 2026-10-16

## Cause and correction

The emulator's real patient journey loader called the assigned-doctor-only Lab consultation endpoint. It returned 403 even when no Lab order existed, preventing Home from showing an otherwise valid ongoing consultation. This was not a Gateway connectivity or MFA error.

Changed only the emulator `BackendJourneyRepository._load`: read the existing patient Lab history endpoint and keep records matching both patientId and consultationId. Published-result restrictions remain enforced by Lab Service. Authorization/availability failures still propagate. No backend role expansion, fake response, schema change or clinical-data mutation.

## Evidence

- Same real account `mobile_noi_c1d9dc`, original consultation and ticket NOI-010: original browser reproduced the Lab 403 and red Home card (`home-journey-before.json`).
- Six new regression cases: before fix, five failed and one passed; after fix, all six passed. They cover no published Lab results, cross-visit/patient filtering, 401/403/503 propagation, and no clinical fetch before consultation.
- Journey data/controller/appointment integration suite: **71 passed, 1 pre-existing skipped**. The skip is an archived separate-pharmacy-payment assertion, unrelated to this fix.
- Dart analyze: **no issues** in the changed loader/new test. Release Web build passed and is served by the running emulator.
- Actual browser/API regression: **7 passed** (`home-journey-after.json`). Home now shows **Hành trình đang diễn ra → Đang khám bệnh**; patient Lab reads return 200, doctor-only and other-patient Lab reads still return 403. Original ticket and consultation remain IN_PROGRESS. Test sessions logged out after successful runs.
- Visually inspected `/tmp/careflow-home-after.png`.
- Mac stack status: all **14 health entries OK**.
- GitNexus index refreshed. Known FTS/BM25 build limitation persists; graph/context/impact and source review were used.

The separate duplicate active booking issue remains documented in `duplicate-active-booking-issue.md`; its records were preserved. No stage, commit, push or merge performed.
