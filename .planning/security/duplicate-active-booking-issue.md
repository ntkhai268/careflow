# Open: Appointment accepts a booking that Queue cannot issue

Observed 2026-10-16, owned local audit stack, with real APIs and Queue log.

Patient `f4e9929c-abd6-44ba-bf75-13741708e4ae` has actual ROOM-01/NOI_TONG_QUAT appointment `65b8e77c-6396-4941-b30f-300e652fd8c6` and IN_PROGRESS queue ticket NOI-010 on 2026-10-16. A second 11:00–11:30 booking `e12df2f6-ecde-414b-b62e-91af4bf5f289` was accepted as CONFIRMED. Its ticket GET returns 404; Queue's AppointmentConfirmed consumer exhausted retries at 10:46:32 because the same patient already has an active department/day entry.

Appointment validates patient/date/time-slot duplicates, while Queue validates active patient/department/day across slots. This mismatch must be resolved at command acceptance with a concurrency-safe rule and documented contracts, and an explicit recovery decision for existing inconsistent appointments/events. Do not describe this specific ticket 404 as transient propagation, attach the old ticket to the new appointment, reset Queue or silently cancel the user's booking.

No booking/queue API or validation symbol was edited by the Mac check-in UI task. Existing conflicting booking preserved. Next implementation requires precise GitNexus impacts and contract review before edits.
