# R7 — Cancellation before confirmation

The real-broker acceptance probe reproduced an out-of-order delivery defect: an appointment was cancelled before Queue had received its confirmation; the later confirmation issued a waiting ticket. Both source envelopes were captured from Appointment, then delivered in reverse order on an isolated broker. No database state was forced to produce the result.

## Invariant and processing

An appointment ID is immutable. Cancellation is terminal; a new booking uses a new ID. Queue must never issue a ticket from a late confirmation after observing cancellation, including after restart or concurrent consumer execution.

`AppointmentEventConsumer` takes a PostgreSQL transaction advisory lock for the payload appointment ID before inspecting its event receipt. Cancellation records a durable marker even when no ticket exists, then applies the existing cancellation transition to any ticket. Confirmation checks the marker before issuing a ticket. Marker, receipt and any ticket/outbox change commit or roll back together. Duplicate cancellation retains its first marker; duplicate event IDs remain ignored. Unsupported events write no marker or receipt.

This lock serializes events for one appointment; unrelated appointments continue independently. Existing room/config locking still protects numbering. This does not replace broker publisher authentication/ACLs or change clinical authorization.

## Migration and retention

V14 adds `queue.appointment_cancellations`: appointment ID primary key, nullable first cancellation event ID and first-observed cancellation timestamp. Existing cancelled tickets with an appointment ID are backfilled; their event ID is unknown and remains null. There is no cross-service foreign key or patient/clinical payload.

Markers persist across restart and daily queue seeding. They are created by actual cancellation processing and migration backfill; the initializer must not invent cancellation for active demo appointments. Do not expire markers independently while corresponding source IDs/events can be replayed. Retention must be coordinated with the operational archive/replay policy.

V14 is additive and does not rewrite queue entries. DBML is synchronized. Older Queue code does not enforce the new invariant; reverting that code is not an accepted functional rollback even if it can open the additive schema. The R7 rollback evidence for Prescription is a separate, same-schema rehearsal.

## Verification

Six PostgreSQL regressions cover cancellation before confirmation, normal ordering plus replay, duplicate cancellation, concurrent ordering, persistence-context restart and unsupported-event rollback. The real Gateway/broker probe repeats the original failure with captured source events. The migration rehearsal restores an existing V13 dump, boots the candidate, checks all legacy cancellation markers and verifies the queue-entry content hash is unchanged.

Evidence and candidate hashes belong to [R7 acceptance](../deployment/r7-acceptance-2026-10-21.md). User operation guidance remains exclusively in [the canonical guide](../../huong-dan-su-dung/25-nghiem-thu-va-ban-giao-r7.md).
