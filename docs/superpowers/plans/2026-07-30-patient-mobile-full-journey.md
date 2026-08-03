# Patient Mobile Full Journey Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a deterministic end-to-end outpatient journey in the Flutter patient app, using real Identity, Patient, Health Record, and Appointment APIs while demoing unfinished clinical services behind replaceable repositories.

**Architecture:** Add a self-contained `features/journey` vertical slice with immutable JSON domain models, a validated state machine, a persistence boundary, and Riverpod controllers. Existing Appointment responses bootstrap a journey; patient-facing widgets consume only journey providers, while `DEMO_MODE` selects deterministic local behavior for unfinished services.

**Tech Stack:** Flutter 3.44.2, Dart 3.12.2, Riverpod 2.6.1, GoRouter 14.8.1, SharedPreferences 2.3.4, qr_flutter 4.1.0, flutter_test.

> **Contract evolution — 2026-08-03:** Queue contract 1.2 adds
> `PHARMACY_DISPENSING`, changes the consultation queue type to `CONSULTATION`,
> and treats `RESULT_REVIEW` as a phase/lane. Result review now activates
> automatically when all required results are available; any older confirmation
> step below is superseded. Pharmacy queue UI/API integration requires a separate
> follow-up slice.

## Global Constraints

- `DEMO_MODE` defaults to `false`; mock data and demo controls must be absent when false.
- Identity, Patient, Health Record, and Appointment continue to use the API Gateway; a remote API failure must never silently fall back to demo data.
- PostgreSQL and RabbitMQ development defaults remain `100.116.233.60` with `careflow` / `careflow`, while environment variables retain override precedence.
- The target model is `Department 1:N ClinicRoom`; MVP data has exactly one
  active room per department and the server derives the room.
- The clinic queue has `PRIORITY`, `NORMAL`, and `RESULT_REVIEW` lanes, FIFO
  within each lane and Round Robin `1:1:1` across non-empty lanes.
- Laboratory orders enter their queue without a second check-in.
- Result review becomes active automatically after all required results are
  available and participates in the `RESULT_REVIEW` lane; Mobile confirmation
  is not required.
- Patient-visible copy is Vietnamese; source files are UTF-8.
- Demo state is namespaced by authenticated patient ID and appointment ID and survives app restart.
- Every behavior change follows RED-GREEN-REFACTOR; no production function is added without a test that first fails for the expected missing behavior.
- Existing routes and real API behaviors remain backward compatible.

---

## File Map

### Domain and data

- `lib/features/journey/domain/journey_models.dart`: enums and immutable JSON value objects.
- `lib/features/journey/domain/journey_transition.dart`: legal state transitions and generated side effects.
- `lib/features/journey/data/journey_store.dart`: persistence interface and corruption result.
- `lib/features/journey/data/shared_preferences_journey_store.dart`: patient/appointment namespaced storage.
- `lib/features/journey/data/journey_repository.dart`: repository interface.
- `lib/features/journey/data/demo_journey_repository.dart`: deterministic mock external events.
- `lib/features/journey/application/journey_controller.dart`: Riverpod-facing orchestration.
- `lib/features/journey/application/journey_providers.dart`: runtime mode and dependency wiring.

### Presentation

- `lib/features/journey/presentation/widgets/journey_status_card.dart`: reusable home/appointment status card.
- `lib/features/journey/presentation/widgets/demo_control_sheet.dart`: demo-only external actor controls.
- `lib/features/journey/presentation/journey_hub_screen.dart`: active journey instruction hub.
- `lib/features/journey/presentation/visit_ticket_screen.dart`: ticket and QR.
- `lib/features/journey/presentation/clinic_queue_screen.dart`: active clinic queue.
- `lib/features/journey/presentation/consultation_screen.dart`: consultation state.
- `lib/features/journey/presentation/laboratory_screen.dart`: orders, payment, lab queue, and results.
- `lib/features/journey/presentation/result_review_screen.dart`: result-review queue.
- `lib/features/journey/presentation/visit_outcome_screen.dart`: diagnosis, prescription, and follow-up.
- `lib/features/journey/presentation/journey_timeline_screen.dart`: ordered visit history.
- `lib/features/journey/presentation/journey_notification_screen.dart`: inbox.

### Existing integration points

- `lib/config/router.dart`: journey routes.
- `lib/screens/home/home_screen.dart`: real active-journey card.
- `lib/screens/main_shell.dart`: provider-backed unread badge.
- `lib/screens/appointment/appointment_screen.dart`: bootstrap/open journey.
- `lib/screens/appointment/appointment_detail_screen.dart`: journey entry point.
- `lib/screens/appointment/booking_step4_screen.dart`: bootstrap after real booking.
- `lib/screens/notification/notification_screen.dart`: replace placeholder with inbox.
- `pubspec.yaml` and `pubspec.lock`: add `qr_flutter: ^4.1.0`.

---

### Task 1: Journey Domain and Validated State Machine

**Files:**

- Create: `frontend/patient-mobile/lib/features/journey/domain/journey_models.dart`
- Create: `frontend/patient-mobile/lib/features/journey/domain/journey_transition.dart`
- Create: `frontend/patient-mobile/test/features/journey/domain/journey_models_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/domain/journey_transition_test.dart`

**Interfaces:**

- Produces: `JourneyStatus`, `PaymentMethod`, `PatientJourney`, `VisitTicket`, `QueueSnapshot`, `LaboratoryOrder`, `LaboratoryResult`, `VisitPayment`, `DiagnosisSummary`, `Prescription`, `PrescriptionItem`, `FollowUpAppointment`, `JourneyTimelineEvent`, and `PatientNotification`.
- Produces: `JourneyTransition.apply(PatientJourney current, JourneyEvent event, {DateTime? now}) -> PatientJourney`.
- Consumes: no application or Flutter UI dependencies.

- [ ] **Step 1: Write failing JSON round-trip tests**

Create fixtures with literal values and assert that `PatientJourney.fromJson(journey.toJson()) == journey`, that UTC timestamps retain their instant, and that missing optional diagnosis/prescription/follow-up fields remain null.

```dart
test('round-trips a laboratory patient journey without losing UTC instants', () {
  final journey = PatientJourney(
    appointmentId: 'apt-47',
    patientId: 'patient-1',
    status: JourneyStatus.waitingLab,
    ticket: VisitTicket(
      code: 'CF-APT-47',
      qrPayload: 'careflow://visit/apt-47',
      queueNumber: '47',
      room: 'Phòng 21 - Lầu 1 khu A',
      expectedWindow: '10:30 - 11:30',
    ),
    laboratoryOrders: const [],
    timeline: const [],
    notifications: const [],
    updatedAt: DateTime.parse('2026-08-18T03:30:00Z'),
  );

  expect(PatientJourney.fromJson(journey.toJson()), journey);
});
```

- [ ] **Step 2: Run model tests and verify RED**

Run:

```text
flutter test test/features/journey/domain/journey_models_test.dart
```

Expected: FAIL because `journey_models.dart` and its types do not exist.

- [ ] **Step 3: Implement immutable models and exhaustive JSON parsing**

Use enum names as wire values, `DateTime.parse(...).toUtc()` for timestamps,
value equality through explicit `operator ==`/`hashCode`, and `copyWith`
sentinels where nullable fields must be clearable.

```dart
enum JourneyStatus {
  booked,
  ticketIssued,
  checkedIn,
  waiting,
  called,
  inConsultation,
  labOrdered,
  paymentPending,
  waitingLab,
  labInProgress,
  labResultReady,
  waitingResultReview,
  resultReview,
  prescribed,
  completed,
}

enum PaymentMethod { online, cash, insurance }
```

- [ ] **Step 4: Run model tests and verify GREEN**

Run the model test command and expect all assertions to pass.

- [ ] **Step 5: Write failing transition table tests**

Cover every legal edge in both direct and laboratory paths. Add explicit
rejection tests for calling before check-in, consultation before called,
results before lab start, and prescribing from `waiting`.

```dart
test('rejects doctor call before staff check-in', () {
  expect(
    () => JourneyTransition.apply(ticketIssued, JourneyEvent.doctorCalled),
    throwsA(isA<InvalidJourneyTransition>()),
  );
});
```

- [ ] **Step 6: Run transition tests and verify RED**

Run:

```text
flutter test test/features/journey/domain/journey_transition_test.dart
```

Expected: FAIL because the transition engine does not exist.

- [ ] **Step 7: Implement the transition table and side effects**

`JourneyEvent` must contain:

```dart
enum JourneyEvent {
  issueTicket,
  staffScannedQr,
  admittedToClinicQueue,
  doctorCalled,
  consultationStarted,
  laboratoryOrdered,
  paymentRequested,
  directPrescriptionIssued,
  paymentAcknowledged,
  laboratoryStarted,
  laboratoryResultsPublished,
  admittedToResultReviewQueue,
  resultReviewCalled,
  finalPrescriptionIssued,
  visitCompleted,
}
```

Each accepted transition appends a Vietnamese timeline event. Patient-relevant
events append a notification with `isRead: false`. Invalid pairs throw
`InvalidJourneyTransition` without changing the input object.

- [ ] **Step 8: Run both domain test files and commit**

Run:

```text
flutter test test/features/journey/domain
dart format lib/features/journey/domain test/features/journey/domain
git add frontend/patient-mobile/lib/features/journey/domain frontend/patient-mobile/test/features/journey/domain
git commit -m "feat(mobile): add patient journey state machine"
```

---

### Task 2: Persistence, Demo Repository, and Riverpod Controller

**Files:**

- Create: `frontend/patient-mobile/lib/features/journey/data/journey_store.dart`
- Create: `frontend/patient-mobile/lib/features/journey/data/shared_preferences_journey_store.dart`
- Create: `frontend/patient-mobile/lib/features/journey/data/journey_repository.dart`
- Create: `frontend/patient-mobile/lib/features/journey/data/demo_journey_repository.dart`
- Create: `frontend/patient-mobile/lib/features/journey/application/journey_controller.dart`
- Create: `frontend/patient-mobile/lib/features/journey/application/journey_providers.dart`
- Create: `frontend/patient-mobile/test/features/journey/data/shared_preferences_journey_store_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/data/demo_journey_repository_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/application/journey_controller_test.dart`

**Interfaces:**

- Consumes: Task 1 domain models and transition engine.
- Produces: `JourneyStore.load/save/delete`, `JourneyRepository`, `DemoJourneyRepository`, `JourneyController`, `journeyControllerProvider`, `journeyForAppointmentProvider`, `activeJourneyProvider`, `unreadJourneyNotificationCountProvider`, and `demoModeProvider`.

- [ ] **Step 1: Write failing namespaced persistence tests**

Use `SharedPreferences.setMockInitialValues({})`. Save two journeys for
different patient IDs with the same appointment ID and assert they never
overwrite each other. Seed malformed JSON and assert `load` returns a corruption
result, removes only that key, and leaves the other patient's journey intact.

- [ ] **Step 2: Run store tests and verify RED**

Run:

```text
flutter test test/features/journey/data/shared_preferences_journey_store_test.dart
```

Expected: FAIL because store types do not exist.

- [ ] **Step 3: Implement the store boundary and SharedPreferences adapter**

Use this key format verbatim:

```dart
String journeyStorageKey(String patientId, String appointmentId) =>
    'careflow.journey.$patientId.$appointmentId';
```

`JourneyLoadResult` has `journey`, `wasCorrupted`, and `errorMessage`. On
corruption, delete only the malformed key and return `wasCorrupted: true`.

- [ ] **Step 4: Run store tests and verify GREEN**

Run the store test file and expect all tests to pass.

- [ ] **Step 5: Write failing repository/controller tests**

Cover:

- bootstrapping a real `Appointment` into `BOOKED -> TICKET_ISSUED`;
- stable ticket number and QR on repeated bootstrap;
- deterministic clinic queue (`peopleAhead: 3`);
- laboratory order branch with a complete mock order;
- online, cash, and insurance payment acknowledgement;
- result-review queue position after one next initial patient;
- switching the authenticated patient clears the active in-memory journey and
  never loads the previous patient's namespaced data;
- invalid events preserve controller state and expose Vietnamese error;
- `demoModeProvider` reads `const bool.fromEnvironment('DEMO_MODE',
  defaultValue: false)`;
- production mode rejects demo advancement with
  `Tính năng đang chờ backend triển khai`.

- [ ] **Step 6: Run repository/controller tests and verify RED**

Run:

```text
flutter test test/features/journey/data/demo_journey_repository_test.dart test/features/journey/application/journey_controller_test.dart
```

Expected: FAIL because repositories and controller do not exist.

- [ ] **Step 7: Implement deterministic repository behavior**

Use stable values derived from appointment ID rather than randomness:

```dart
final suffix = appointmentId.codeUnits.fold<int>(0, (a, b) => a + b);
final queueNumber = '${40 + (suffix % 20)}';
final ticketCode = 'CF-${appointmentId.toUpperCase()}';
final qrPayload = 'careflow://visit/$appointmentId';
```

Generate one `Xét nghiệm công thức máu` and one `X-quang ngực thẳng` order for
the lab branch. Results use medically plausible demo labels and explicitly show
`Dữ liệu mô phỏng` in their source metadata.

- [ ] **Step 8: Implement Riverpod wiring and controller**

`JourneyController` exposes:

```dart
Future<PatientJourney> bootstrap({
  required Appointment appointment,
  required String patientId,
});
Future<void> advance(JourneyEvent event);
Future<void> acknowledgePayment(PaymentMethod method);
Future<void> markNotificationRead(String notificationId);
Future<void> resetCurrentJourney();
```

The state type is `AsyncValue<PatientJourney?>`. Repository exceptions become
`AsyncError`; invalid transitions retain the current journey and set a separate
`journeyActionErrorProvider`.

- [ ] **Step 9: Run data/application tests and commit**

Run:

```text
flutter test test/features/journey/data test/features/journey/application
dart format lib/features/journey/data lib/features/journey/application test/features/journey/data test/features/journey/application
git add frontend/patient-mobile/lib/features/journey frontend/patient-mobile/test/features/journey
git commit -m "feat(mobile): persist and control demo journeys"
```

---

### Task 3: Visit Ticket, Clinic Queue, and Demo Controls

**Files:**

- Modify: `frontend/patient-mobile/pubspec.yaml`
- Modify: `frontend/patient-mobile/pubspec.lock`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/widgets/journey_status_card.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/widgets/demo_control_sheet.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/journey_hub_screen.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/visit_ticket_screen.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/clinic_queue_screen.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/visit_ticket_screen_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/clinic_queue_screen_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/demo_control_sheet_test.dart`

**Interfaces:**

- Consumes: Task 2 providers/controller.
- Produces: patient-facing entry screens for ticket issuance through
  `IN_CONSULTATION`, plus a reusable status card for later integration.

- [ ] **Step 1: Write failing ticket and queue widget tests**

Override journey providers with fixed `AsyncData` values. Assert:

- ticket shows hospital, specialty, room, expected window, number, and QR;
- `TICKET_ISSUED` says to present QR to staff and never says the patient is
  already in the active queue;
- `WAITING` shows `Còn 3 người phía trước`;
- `CALLED` shows `Đã đến lượt bạn` and the clinic room;
- loading, error, and unavailable production states have explicit copy.

- [ ] **Step 2: Run presentation tests and verify RED**

Run:

```text
flutter test test/features/journey/presentation/visit_ticket_screen_test.dart test/features/journey/presentation/clinic_queue_screen_test.dart
```

Expected: FAIL because screens do not exist.

- [ ] **Step 3: Add QR dependency and implement the three patient screens**

Add:

```yaml
qr_flutter: ^4.1.0
```

Render `QrImageView(data: ticket.qrPayload, size: 156)` inside a semantic label
`Mã QR phiếu khám`. The journey hub chooses one primary destination from the
current state and never exposes staff actions as normal patient buttons.

- [ ] **Step 4: Run ticket/queue tests and verify GREEN**

Run the two widget test files and expect both to pass.

- [ ] **Step 5: Write failing demo control visibility/action tests**

Assert the control is absent when `demoModeProvider == false`, visible with the
heading `Điều khiển mô phỏng` when true, and exposes only the next legal external
event. Verify tapping `Mô phỏng nhân viên quét QR` invokes one controller event.

- [ ] **Step 6: Run demo control test and verify RED**

Run:

```text
flutter test test/features/journey/presentation/demo_control_sheet_test.dart
```

Expected: FAIL because the demo control does not exist.

- [ ] **Step 7: Implement demo control and reset confirmation**

The sheet watches current journey status, maps it to one or two valid external
events, and requires confirmation before resetting the current appointment.
No `DEMO_MODE=false` build may instantiate this widget.

- [ ] **Step 8: Run Task 3 tests and commit**

Run:

```text
flutter pub get
flutter test test/features/journey/presentation/visit_ticket_screen_test.dart test/features/journey/presentation/clinic_queue_screen_test.dart test/features/journey/presentation/demo_control_sheet_test.dart
dart format lib/features/journey/presentation test/features/journey/presentation
git add frontend/patient-mobile
git commit -m "feat(mobile): add visit ticket and clinic queue"
```

---

### Task 4: Consultation, Laboratory Orders, Payment, and Result Review

**Files:**

- Create: `frontend/patient-mobile/lib/features/journey/presentation/consultation_screen.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/laboratory_screen.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/result_review_screen.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/consultation_screen_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/laboratory_screen_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/result_review_screen_test.dart`

**Interfaces:**

- Consumes: journey models, controller, and providers from Tasks 1–2.
- Produces: all patient views from `IN_CONSULTATION` through
  `RESULT_REVIEW`.

- [ ] **Step 1: Write failing consultation widget tests**

Assert that `IN_CONSULTATION` shows doctor/room and `Bác sĩ đang khám`; demo
controls can branch to direct prescription or laboratory orders, while normal
patient UI cannot choose the doctor's clinical decision.

- [ ] **Step 2: Run consultation tests and verify RED**

Run the consultation widget test and expect missing-screen failure.

- [ ] **Step 3: Implement consultation screen and verify GREEN**

Build a status-first screen with no clinical input controls. Run the test until
it passes.

- [ ] **Step 4: Write failing laboratory/payment widget tests**

For `LAB_ORDERED` and `PAYMENT_PENDING`, assert order cards include department,
destination, preparation note, and price. Assert all three payment methods are
available and cash copy says `Thanh toán tại bệnh viện`. For `WAITING_LAB` and
`LAB_IN_PROGRESS`, assert no second check-in button exists. For
`LAB_RESULT_READY`, assert result values and `Dữ liệu mô phỏng` are visible.

- [ ] **Step 5: Run laboratory tests and verify RED**

Run the laboratory widget test and expect missing-screen failure.

- [ ] **Step 6: Implement laboratory/payment screen and verify GREEN**

Payment selection calls `acknowledgePayment`. Online mode displays a simulated
success receipt only in Demo Mode; production mode displays the unavailable
backend message. Cash and insurance record acknowledgement without claiming
electronic settlement.

- [ ] **Step 7: Write failing result-review queue tests**

Assert `WAITING_RESULT_REVIEW` displays:

```text
Quay lại Phòng 21
Bạn đã được đưa vào hàng chờ đọc kết quả. Vui lòng quay lại và chờ được gọi.
```

Assert `RESULT_REVIEW` displays that the doctor is reviewing results.

- [ ] **Step 8: Implement result-review screen and run Task 4 tests**

Run:

```text
flutter test test/features/journey/presentation/consultation_screen_test.dart test/features/journey/presentation/laboratory_screen_test.dart test/features/journey/presentation/result_review_screen_test.dart
dart format lib/features/journey/presentation test/features/journey/presentation
git add frontend/patient-mobile/lib/features/journey/presentation frontend/patient-mobile/test/features/journey/presentation
git commit -m "feat(mobile): add laboratory and result review flow"
```

---

### Task 5: Visit Outcome, Timeline, and Notification Inbox

**Files:**

- Create: `frontend/patient-mobile/lib/features/journey/presentation/visit_outcome_screen.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/journey_timeline_screen.dart`
- Create: `frontend/patient-mobile/lib/features/journey/presentation/journey_notification_screen.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/visit_outcome_screen_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/journey_timeline_screen_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/presentation/journey_notification_screen_test.dart`

**Interfaces:**

- Consumes: completed journey and notification controller actions.
- Produces: post-visit information and provider-backed inbox.

- [ ] **Step 1: Write failing visit outcome tests**

Use a completed fixture and assert diagnosis, each medicine's dosage/route/
frequency/duration, caution text, follow-up date, and room are visible. A
completed direct path with no laboratory orders must not render an empty lab
section.

- [ ] **Step 2: Run outcome test and verify RED**

Run the outcome widget test and expect missing-screen failure.

- [ ] **Step 3: Implement outcome screen and verify GREEN**

Use separate cards for diagnosis, prescription, and follow-up. Optional fields
must be omitted rather than rendered as `null`.

- [ ] **Step 4: Write failing timeline and inbox tests**

Assert timeline events are chronological and carry Vietnamese labels. Assert
inbox notifications are newest-first, unread records have a visual marker,
tapping one marks it read, and the unread provider count changes from 2 to 1.

- [ ] **Step 5: Run timeline/inbox tests and verify RED**

Run both widget test files and expect missing-screen failures.

- [ ] **Step 6: Implement timeline and inbox and verify GREEN**

The inbox delegates persistence to `JourneyController.markNotificationRead`.
It shows a neutral empty state when no journey notification exists.

- [ ] **Step 7: Run Task 5 tests and commit**

Run:

```text
flutter test test/features/journey/presentation/visit_outcome_screen_test.dart test/features/journey/presentation/journey_timeline_screen_test.dart test/features/journey/presentation/journey_notification_screen_test.dart
dart format lib/features/journey/presentation test/features/journey/presentation
git add frontend/patient-mobile/lib/features/journey/presentation frontend/patient-mobile/test/features/journey/presentation
git commit -m "feat(mobile): add visit results and notifications"
```

---

### Task 6: Router, Home, Appointment, and Main Shell Integration

**Files:**

- Modify: `frontend/patient-mobile/lib/config/router.dart`
- Modify: `frontend/patient-mobile/lib/screens/home/home_screen.dart`
- Modify: `frontend/patient-mobile/lib/screens/main_shell.dart`
- Modify: `frontend/patient-mobile/lib/screens/appointment/appointment_screen.dart`
- Modify: `frontend/patient-mobile/lib/screens/appointment/appointment_detail_screen.dart`
- Modify: `frontend/patient-mobile/lib/screens/appointment/booking_step4_screen.dart`
- Modify: `frontend/patient-mobile/lib/screens/notification/notification_screen.dart`
- Create: `frontend/patient-mobile/test/features/journey/integration/journey_router_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/integration/home_journey_integration_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/integration/appointment_journey_integration_test.dart`
- Create: `frontend/patient-mobile/test/features/journey/integration/main_shell_notification_test.dart`

**Interfaces:**

- Consumes: all earlier tasks and existing `AppointmentService`.
- Produces: a discoverable full journey from real booking/list/detail flows.

- [ ] **Step 1: Write failing router tests**

Assert the following paths construct without router errors:

```text
/journey/:appointmentId
/journey/:appointmentId/ticket
/journey/:appointmentId/queue
/journey/:appointmentId/consultation
/journey/:appointmentId/laboratory
/journey/:appointmentId/result-review
/journey/:appointmentId/outcome
/journey/:appointmentId/timeline
```

- [ ] **Step 2: Run router test and verify RED**

Run the router test and expect route-not-found failures.

- [ ] **Step 3: Add journey routes and verify GREEN**

Each route passes `appointmentId` explicitly and lets the screen watch
`journeyForAppointmentProvider(appointmentId)`. Keep all existing routes.

- [ ] **Step 4: Write failing appointment bootstrap tests**

Inject a fake `AppointmentService` and journey repository. Assert a successful
real booking calls `bootstrap` once with the returned appointment, appointment
list/detail can open the journey, and an API failure displays the real error
without creating a demo appointment.

- [ ] **Step 5: Run appointment integration tests and verify RED**

Run the appointment integration test and expect missing bootstrap behavior.

- [ ] **Step 6: Integrate real appointments and verify GREEN**

After `createAppointment` succeeds, bootstrap the journey before navigating to
the visit ticket. Existing appointments bootstrap lazily on first open and
reuse persisted state afterward.

- [ ] **Step 7: Write failing home and notification badge tests**

Assert the home screen replaces its hard-coded appointment card with the active
journey card and navigates to its contextual destination. Assert the main shell
badge shows provider count, hides at zero, and never uses hard-coded `330`.
Log out and authenticate as a different patient in the provider test; assert
that the previous patient's active card and unread count are absent.

- [ ] **Step 8: Replace placeholders and verify integration tests**

Replace `NotificationScreen` body with `JourneyNotificationScreen`. Connect the
home bell to the notification tab/route. Keep the five-tab layout.

- [ ] **Step 9: Run Task 6 tests and commit**

Run:

```text
flutter test test/features/journey/integration
dart format lib/config/router.dart lib/screens/home lib/screens/main_shell.dart lib/screens/appointment lib/screens/notification test/features/journey/integration
git add frontend/patient-mobile
git commit -m "feat(mobile): connect appointments to patient journey"
```

---

### Task 7: End-to-End Demo Regression, Lint Cleanup, and Build

**Files:**

- Create: `frontend/patient-mobile/test/features/journey/integration/full_demo_journey_test.dart`
- Modify: only files reported by `flutter analyze` under
  `frontend/patient-mobile/lib` when the change is behavior-preserving.
- Modify: `frontend/patient-mobile/README.md` if it exists; otherwise create it.

**Interfaces:**

- Consumes: complete implementation.
- Produces: executable demo instructions and final verification evidence.

- [ ] **Step 1: Write a failing full journey widget test**

Start from a real-shaped appointment fixture and advance:

```text
TICKET_ISSUED
-> WAITING
-> CALLED
-> IN_CONSULTATION
-> PAYMENT_PENDING
-> WAITING_LAB
-> LAB_IN_PROGRESS
-> LAB_RESULT_READY
-> WAITING_RESULT_REVIEW
-> RESULT_REVIEW
-> PRESCRIBED
-> COMPLETED
```

At every state, assert the patient instruction and available destination. Assert
the final screen contains prescription and follow-up information and the inbox
contains all required event notifications.

- [ ] **Step 2: Run full journey test and verify RED**

Run:

```text
flutter test test/features/journey/integration/full_demo_journey_test.dart
```

Expected: FAIL at the first missing or incorrectly wired behavior.

- [ ] **Step 3: Make only the minimal integration corrections and verify GREEN**

Fix one observed failure at a time and rerun the single test after each change.
Do not weaken assertions to match broken behavior.

- [ ] **Step 4: Remove analyzer issues**

Replace debug `print` calls in `auth_service.dart` with behavior-preserving
removal, remove the unused Health Record variable, migrate deprecated
`DropdownButtonFormField.value` to `initialValue`, and apply super parameters
where reported. Run `flutter analyze` after each focused edit.

- [ ] **Step 5: Document demo execution**

Document:

```text
flutter run --dart-define=DEMO_MODE=true
flutter run --dart-define=DEMO_MODE=false
flutter run --dart-define=DEMO_MODE=true --dart-define=API_BASE_URL=<gateway>
```

Explain which capabilities call real APIs, how to open `Điều khiển mô phỏng`,
and how to reset only the active journey.

- [ ] **Step 6: Run full verification**

Run exactly:

```text
flutter test
flutter analyze
flutter build apk --debug --dart-define=DEMO_MODE=true
```

Expected: all tests pass, analyzer reports no issues, and APK build exits zero.

- [ ] **Step 7: Verify repository diff and commit**

Run:

```text
git diff --check
git status --short
git add frontend/patient-mobile
git commit -m "test(mobile): verify full outpatient demo journey"
```

The task is complete only if `git status --short` is empty after commit.
