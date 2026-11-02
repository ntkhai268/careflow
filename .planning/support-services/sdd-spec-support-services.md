# Spec-Driven Development (SDD) Specification: Support Services (`careflow-ai-service`, `careflow-notification-service`, `careflow-analytics-service`)

## 1. Bounded Context & Responsibilities

### 1.1. `careflow-ai-service`
- **Bounded Context**: Local Extractive Clinical Evidence Retrieval (`careflow-local-evidence-v1`), Clinical Guideline Knowledge Base Governance (`DRAFT` -> `APPROVED` -> `RETIRED`), and Voice/Text-to-Clinical-Fields Extraction.
- **Core Invariants**:
  - Only authenticated doctors (`role = DOCTOR`) actively assigned to the target consultation (`IN_PROGRESS`, `AWAITING_CLS`, `AWAITING_REVIEW`, `READY_TO_COMPLETE`, `COMPLETED`) may query clinical assistance (`AiAccess`).
  - Extractive retrieval only: guideline source text is treated strictly as data (never as executable instructions), ranked by explicit query token overlap (`x2.0`) and clinical context facts overlap (`x0.25`).
  - Interactions (`ai_clinical_interactions`) are encrypted at rest with `AES/GCM/NoPadding` (`ClinicalCipher`) bound to `id:actorId:consultationId` as AAD and automatically purged after retention expiry (`1–30 days`).
  - Guideline governance (`ai_guideline_sources`): `ADMIN` imports HTTPS-only sources (`DRAFT`); a `DOCTOR` different from `imported_by` verifies the SHA-256 `expectedHash` to transition to `APPROVED`.

### 1.2. `careflow-notification-service` (Schema: `notification`)
- **Bounded Context**: Multi-channel Patient Notification Inbox (`IN_APP` WebSocket `/queue/notifications` + Session-Bound Firebase Push `FCM`), Patient Recipient Mapping (`patient_recipients`), and Transactional Outbox (`outbox_events`).
- **Core Invariants**:
  - Idempotent consumption of domain events (`AppointmentConfirmed`, `VisitTicketIssued`, `PatientCheckedIn`, `QueueNearTurn`, `PatientCalled`, `QueueEntryMissed`, `LabOrderCreated`, `LabOrderReadyForExecution`, `LabResultAvailable`, `AllRequiredResultsAvailable`, `PrescriptionIssued`, `FollowUpScheduled`) enforced via `uk_notification_source_recipient_type (source_event_id, recipient_user_id, notification_type)`.
  - Session-bound push security (`DeviceInstallationService`, `PushDeliveryService`):
    - Device registrations acquire deterministic PostgreSQL transaction advisory locks on `push-token:<token>` and `push-device:<userId>:<deviceId>`.
    - Before dispatching any push notification (`PushDelivery`), `PushDeliveryService` verifies:
      1. Delivery TTL (`expires_at > now()`, 5-minute window).
      2. Binding integrity (`bindingId`, `recipientUserId`, `registrationToken`).
      3. Active session status via `PushSessionClient.active(userId, sessionId)` (disables device and marks `SESSION_REVOKED` if revoked).
      4. Active patient ownership via `PatientOwnershipClient.owner(patientId)` (marks `PATIENT_OWNERSHIP_CHANGED` if mismatched).

### 1.3. `careflow-analytics-service` (Schema: `analytics`)
- **Bounded Context**: Operational Hospital Metrics Projection (`R5_V1` cohort definitions), Real-time RabbitMQ Snapshot Ingestion (`APPOINTMENT`, `QUEUE`, `CONSULTATION`), Scheduled Cursor Reconciliation (`ReconciliationService`), and Privacy-Preserving Cohort Aggregation (`ReportCalculator`).
- **Core Invariants**:
  - Strictly `ADMIN`-only access (`AnalyticsController`).
  - Version/timestamp-ordered snapshot acceptance (`SnapshotStore.accept`): detects `APPLIED`, `STALE_SNAPSHOT`, `UNCHANGED`, and `SNAPSHOT_CONFLICT`, storing immutable history in `analytics.snapshot_history` and current state in `analytics.source_projection`.
  - Cohort metrics (`ReportCalculator.calculate`): computes booking counts, scheduled vs. walk-in arrivals, cancellation rate, missed visits/calls, and `p50`/`p95`/mean timings for initial wait, result-review wait, and consultation duration without exposing patient-identifiable records.

---

## 2. Database Ownership

| Service | Schema / Tables | Key Constraints & Columns |
| :--- | :--- | :--- |
| `careflow-ai-service` | `public.ai_guideline_sources`, `public.ai_clinical_interactions` | `content_hash` (SHA-256), `status` (`DRAFT`, `APPROVED`, `RETIRED`), `content_cipher` (AES-GCM with AAD `id:actor:consultation`), `expires_at` |
| `careflow-notification-service` | `notification.notifications`, `notification.device_installations`, `notification.push_deliveries`, `notification.patient_recipients`, `notification.outbox_events` | `status` (`DELIVERED`, `READ`), `PushDeliveryStatus` (`PENDING`, `SENT`, `FAILED`, `DEAD`), `binding_id`, `session_id` |
| `careflow-analytics-service` | `analytics.snapshot_history`, `analytics.source_projection`, `analytics.event_receipts`, `analytics.sync_state`, `analytics.operation_audit` | `(source, source_id, synthetic)` PK on `source_projection`, advisory locks `510021` (write/rebuild) and `510022` (reconcile lease) |

---

## 3. Sequence Diagram (Eraser.io DSL)

```eraser
title CareFlow Event-Driven Notification, Session-Bound Push & Analytics Projection

DomainService [icon: server, label: "Appointment / Queue / Consultation / Lab / Prescription"]
RabbitMQ [icon: message-circle, label: "RabbitMQ Broker"]
Notification [icon: bell, label: "careflow-notification-service"]
Identity [icon: lock, label: "careflow-identity-service"]
PatientService [icon: user, label: "careflow-patient-service"]
Analytics [icon: bar-chart-2, label: "careflow-analytics-service"]

// 1. Domain Event to In-App & Session-Bound Push Notification
DomainService -> RabbitMQ: Publish EventEnvelope (e.g. PatientCalled / LabResultAvailable)
RabbitMQ -> Notification: Consume EventEnvelope
Notification -> PatientService: Verify current owner userId for patientId
Notification -> Notification: Save Notification(DELIVERED) + OutboxEvent + PushDelivery(PENDING)
Notification --> PatientApp: WebSocket STOMP /user/queue/notifications (after commit)

// 2. Scheduled Push Delivery Verification
Notification -> Identity: PushSessionClient.active(userId, sessionId)
Notification -> PatientService: PatientOwnershipClient.owner(patientId)
Notification -> FCM: Send privacy-safe push notification with bindingId

// 3. Analytics Snapshot Ingestion & Cohort Calculation
RabbitMQ -> Analytics: Consume domain event (analytics.*.events)
Analytics -> DomainService: GET /internal/analytics/records?id={aggregateId}&limit=1
Analytics -> Analytics: SnapshotStore.accept() -> snapshot_history + source_projection
```
