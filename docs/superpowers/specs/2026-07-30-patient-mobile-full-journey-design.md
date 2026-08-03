# Patient Mobile Full Journey — Design Specification

**Date:** 2026-07-30

**Status:** Approved

**Target branch:** `feature/khai/patient-mobile`

> **Contract evolution — 2026-08-03:** Queue contract 1.2 models
> `QueueType=CONSULTATION|LAB_EXECUTION|PHARMACY_DISPENSING` and keeps
> `RESULT_REVIEW` as a consultation phase/scheduling lane. This dated design
> keeps its existing patient UI states; pharmacy queue tracking is a follow-up
> vertical slice rather than an implicit change to the completed plan.

## 1. Objective

Build a complete, demonstrable outpatient journey in the CareFlow Flutter
patient application, from booking through post-visit records.

The application must use existing backends where their contracts are already
available and isolate unfinished capabilities behind deterministic demo data
sources. Demo code must not leak into production behavior.

## 2. Product Principles

1. The patient sees one coherent visit journey, not a list of microservices.
2. A booked appointment is automatically confirmed and receives an electronic
   visit ticket.
3. The electronic ticket contains a QR code, clinic room, expected time window,
   and issued queue number. The target model is `Department 1:N ClinicRoom`;
   MVP data provides exactly one active room per department, so the
   server derives the room instead of accepting or randomizing it on Mobile.
4. A queue number printed on a ticket is not an active queue entry. After the
   ticket QR is scanned and the visit becomes `CHECKED_IN`, the patient enters
   the `PRIORITY` or `NORMAL` lane; FIFO is preserved within that lane.
5. Clinical and laboratory staff actions are never presented as patient
   actions. In demo builds, a separate control surface simulates those external
   actors.
6. A laboratory order is automatically received by the laboratory workflow.
   The patient does not check in a second time.
7. When all ordered results are available, the patient is asked to return to
   the clinic. After confirming arrival, the patient enters `RESULT_REVIEW`,
   which joins the clinic's Round Robin `1:1:1` scheduling cycle.
8. Analytics and AI are outside the patient journey implementation. They may
   consume the resulting data later but must not block the core flow.

## 3. Delivery Approach

Use a hybrid repository architecture:

```text
Flutter UI
  -> Riverpod providers/controllers
    -> repository interfaces
      -> remote data source (available backend)
      -> demo data source (unfinished backend)
```

The UI and domain model depend only on repository interfaces. Replacing a demo
data source with a remote implementation must not require rewriting screens or
changing navigation.

### 3.1 Real backend capabilities

These capabilities use the API Gateway and existing service contracts:

- Identity: registration, login, token refresh, logout.
- Patient: patient profile creation, lookup, and update.
- Patient health records: list, detail, create, update, and file upload.
- Appointment: department and slot discovery, booking, list, detail, and
  cancellation.

Appointment responses are adapted into the mobile journey model. Backend status
values remain authoritative for appointment lifecycle operations, while demo
state augments only the downstream clinical journey.

### 3.2 Demo-backed capabilities

These capabilities use deterministic local repositories until their backend
contracts are deployed:

- Visit ticket and QR payload.
- Clinic queue and queue notifications.
- Consultation progress.
- Laboratory order, payment state, laboratory queue, and results.
- Result-review queue.
- Final diagnosis.
- Prescription.
- Follow-up appointment.
- Patient inbox and journey timeline.

Demo state is stored with `shared_preferences`, namespaced by appointment ID.
Logging out must not expose one account's demo journey to another account.

## 4. Runtime Modes

The build flag is:

```text
DEMO_MODE=true|false
```

Default value: `false`.

In `DEMO_MODE=true`:

- Remote repositories are still used for Identity, Patient, Health Record, and
  Appointment.
- Demo repositories are used for unfinished capabilities.
- A clearly labelled developer/demo control is available from the active visit.
- Each control action advances exactly one valid external event.

In `DEMO_MODE=false`:

- Demo controls are absent from the widget tree.
- Mock clinical data is never generated.
- Unavailable capabilities show an explicit temporary-unavailable state rather
  than silently returning demo data.

## 5. Patient Journey State Machine

The aggregate root is `PatientJourney`, keyed by `appointmentId`.

### 5.1 Main path

```text
BOOKED
  -> TICKET_ISSUED
  -> CHECKED_IN
  -> WAITING
  -> CALLED
  -> IN_CONSULTATION
```

The automatic transition `BOOKED -> TICKET_ISSUED` happens when an appointment
is successfully created or when a confirmed appointment is first opened.

The staff QR scan produces `CHECKED_IN`; queue admission immediately produces
`WAITING`.

### 5.2 Direct completion path

```text
IN_CONSULTATION
  -> PRESCRIBED
  -> COMPLETED
```

### 5.3 Laboratory path

```text
IN_CONSULTATION
  -> LAB_ORDERED
  -> PAYMENT_PENDING
  -> WAITING_LAB
  -> LAB_IN_PROGRESS
  -> LAB_RESULT_READY
  -> WAITING_RESULT_REVIEW
  -> RESULT_REVIEW
  -> PRESCRIBED
  -> COMPLETED
```

Payment methods are `ONLINE`, `CASH`, and `INSURANCE`. Choosing cash records
that payment will be collected at the hospital; it does not pretend an online
payment succeeded.

### 5.4 Invalid transitions

The controller must reject invalid transitions without mutating the journey.
Examples:

- Calling a patient before check-in.
- Starting consultation before the patient is called.
- Publishing results before laboratory work starts.
- Prescribing before initial consultation or result review.

The UI displays the error in Vietnamese and retains the previous state.

## 6. Domain Model

`PatientJourney` contains:

- appointment identity and patient identity;
- current `JourneyStatus`;
- visit ticket;
- clinic queue snapshot;
- consultation summary;
- zero or more laboratory orders;
- optional payment;
- optional result-review queue snapshot;
- optional diagnosis;
- optional prescription;
- optional follow-up;
- ordered timeline events;
- ordered inbox notifications;
- `updatedAt`.

Supporting value objects:

- `VisitTicket`
- `QueueSnapshot`
- `ConsultationSummary`
- `LaboratoryOrder`
- `LaboratoryResult`
- `VisitPayment`
- `DiagnosisSummary`
- `Prescription`
- `PrescriptionItem`
- `FollowUpAppointment`
- `JourneyTimelineEvent`
- `PatientNotification`

All local models support JSON round-tripping for persistence. Timestamps are
stored as ISO-8601 UTC strings and formatted in Asia/Bangkok local time for the
Vietnamese UI.

## 7. Queue Rules

### 7.1 Initial clinic queue

- Three logical lanes exist per clinic room and session: `PRIORITY`, `NORMAL`,
  and `RESULT_REVIEW`.
- FIFO applies inside each lane; the room scheduler suggests patients using
  Round Robin `1:1:1` and skips empty lanes.
- Only `CHECKED_IN` initial patients and confirmed-return review patients are active.
- The electronic ticket's printed number remains stable.
- `peopleAhead` and expected wait are derived demo values and never presented as
  guaranteed times.
- A called patient can be marked missed by a future backend, but missed-turn
  handling is outside this mobile increment.

### 7.2 Laboratory queue

- A paid or payment-acknowledged order enters `WAITING_LAB`.
- No QR scan or secondary check-in is required.
- The patient sees destination, preparation notes, and queue progress.

### 7.3 Result-review queue

- `LAB_RESULT_READY` creates a result-review queue entry.
- The entry is positioned after the next waiting initial patient.
- Mobile copy explains that the patient should return to the original clinic
  room and wait for the result review call.

## 8. Screens and Navigation

The application adds the following patient-facing routes:

- Active journey hub.
- Electronic visit ticket.
- Clinic queue.
- Consultation status.
- Laboratory orders and order detail.
- Payment method and payment status.
- Laboratory queue.
- Laboratory results.
- Result-review queue.
- Visit outcome containing diagnosis, prescription, and follow-up.
- Journey timeline.
- Notification inbox.

Existing login, profile, health-record, booking, appointment-list, and
appointment-detail routes remain available.

The home screen prioritizes the active journey. Its primary card shows current
status, the next instruction, room, queue information when applicable, and one
contextual patient action.

## 9. Demo Control

The demo control is visible only when `DEMO_MODE=true`.

It is visually separated from patient actions and labelled
`Điều khiển mô phỏng`. It exposes only valid next external events:

- simulate staff QR scan;
- simulate doctor call;
- simulate consultation start;
- choose consultation outcome with or without laboratory orders;
- simulate laboratory start;
- simulate laboratory result publication;
- simulate result-review call;
- simulate final prescription and follow-up.

Each event:

1. validates the current state;
2. mutates and persists the journey;
3. appends a timeline event;
4. creates an inbox notification when the event is patient-relevant;
5. updates all listening screens through Riverpod.

A reset action is available in demo control and affects only the current
appointment journey.

## 10. Notifications

For demo-backed events, notifications are in-app records rather than OS push
notifications. The inbox supports read/unread status and unread count.

Required notifications:

- successful check-in;
- approaching turn;
- called into clinic;
- new laboratory order;
- laboratory result available;
- called for result review;
- prescription available;
- follow-up scheduled.

The repository boundary must allow a future WebSocket/FCM adapter to feed the
same notification model.

## 11. Error and Offline Behavior

- Existing real API errors continue through the shared Dio error mapping.
- A real API failure never silently falls back to demo data.
- Demo persistence failures show a retryable Vietnamese error.
- Empty states explain what the patient should do next.
- Screens tolerate missing optional clinical values.
- A malformed stored demo journey is discarded only for its appointment; the
  app recreates the initial ticket state and records the recovery.

## 12. Server Configuration

For the services used by the real mobile flow, development defaults connect to:

- PostgreSQL host: `100.116.233.60`
- RabbitMQ host: `100.116.233.60`
- PostgreSQL credentials: `careflow` / `careflow`
- RabbitMQ credentials: `careflow` / `careflow`

Every value remains overridable by environment variables. Service-to-service
HTTP URLs remain local or gateway-routed; only shared infrastructure is remote.

Secrets beyond the existing development credentials must not be committed.

## 13. Test Strategy

All behavior is implemented test-first.

Required automated coverage:

- journey state transition success and rejection;
- JSON persistence round-trip and corrupted-data recovery;
- ticket generation and stable queue number;
- laboratory and result-review branching;
- notification creation and unread count;
- repository selection for demo and production mode;
- Riverpod controller state propagation;
- widget tests for the active journey hub and each major state;
- router tests for new destinations;
- regression tests for existing auth/profile/appointment behavior.

Verification commands:

```text
flutter test
flutter analyze
flutter build apk --debug --dart-define=DEMO_MODE=true
```

The implementation is complete only when the full journey can be traversed
deterministically from a real appointment and all verification commands finish
without errors.

## 14. Out of Scope

- Production payment gateway integration.
- Production QR scanner used by staff.
- Real-time Queue, Consultation, Laboratory, Prescription, or Notification
  backend integration when those contracts are not deployed.
- Emergency prioritization.
- Multi-room queue optimization.
- AI clinical recommendations.
- Analytics dashboards.
