# SDD Specification — Patient Mobile (`frontend/patient-mobile`)

## 1. Scope & Architectural Goals
- Decompose oversized Dart files in `frontend/patient-mobile` while preserving 100% of existing public symbols, import paths, JSON serialization contracts, and widget keys.
- Maintain strict patient ownership checks (`JourneyOwnershipMismatch` and `journeyAccountScopeProvider` validation) and graceful fallback behavior when auxiliary services (e.g., Queue or local demo payment store) are unavailable.

---

## 2. Journey Domain Models Decomposition (`lib/features/journey/domain/`)
- `journey_models.dart`: Library entrypoint declaring `JourneyStatus`, `PaymentMethod` (`PaymentMethodContract`), `VisitSettlementStatus` (`VisitSettlementStatusContract`), `PatientJourney`, and shared JSON primitives (`_utc`, `_iso`, `_map`, `_objects`, `_sameList`, `_unset`).
- `journey_ticket_queue_models.dart` (`part of 'journey_models.dart'`): `VisitTicket`, `QueueSnapshot`, `JourneyTimelineEvent`, and `PatientNotification`.
- `journey_lab_payment_models.dart` (`part of 'journey_models.dart'`): `LaboratoryResult`, `LaboratoryOrder`, `VisitPayment`, and `VisitSettlement`.
- `journey_clinical_models.dart` (`part of 'journey_models.dart'`): `DiagnosisSummary`, `PrescriptionItem`, `Prescription`, and `FollowUpAppointment`.

---

## 3. Journey Repository & Mapper Decomposition (`lib/features/journey/data/`)
- `backend_journey_repository.dart`: `JourneyOwnershipMismatch`, `BackendJourneyActionUnavailable`, `BackendJourneyResources`, and `BackendJourneyRepository` (service orchestration and 404/5xx queue fault tolerance). Re-exports `backend_journey_mapper.dart`.
- `backend_journey_mapper.dart`: `BackendJourneyMapper` and value-coercion helpers translating backend DTO maps into `PatientJourney` domain objects.

---

## 4. Auth & Appointment Detail Screen Decomposition (`lib/screens/`)
- `lib/screens/auth/widgets/login_method_widgets.dart`: Extracted `loginQuestions`, `LoginMethodCard`, `AuthNoticeBanner`, and `showSecretQuestionPicker` modal sheet from `login_methods_screen.dart`.
- `lib/screens/appointment/widgets/appointment_detail_cards.dart`: Extracted `AppointmentStatusHeaderCard`, `AppointmentInfoCard`, `AppointmentPaymentReceiptCard`, `AppointmentVisitTicketActionCard`, `AppointmentStatusTimelineCard`, and `AppointmentCancelActionBar` from `appointment_detail_screen.dart`.
