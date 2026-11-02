# SDD Specification — Hospital Web Portal (`frontend/doctor-web`)

## 1. Architectural Context & Boundaries
`frontend/doctor-web` is the Next.js 16 (App Router, React 19, TypeScript) clinical & administrative web portal for Doctors, Reception/Pharmacy Staff, Lab Technicians, and Hospital Administrators.

### Core UI/UX & Engineering Invariants
1. **3-State Loading Pattern**: All list/table/summary views MUST maintain explicit `data: T[] | null = null`, `isLoading: boolean`, and `error: string | null` states (`isLoading` -> `error` -> `EmptyState` -> `data`), preventing premature empty-state flashes or mistaking failed network calls for empty clinical records.
2. **Searchable Combobox & Outside-Click Dismiss**: All master-data selection controls (ICD-10, Medicine Catalog, Lab Catalog, Departments, Rooms, Doctors) MUST filter existing records and dismiss dropdown menus on outside click.
3. **Clinical Safety Guards**:
   - **Allergy Verification**: Patient allergy load errors MUST display an explicit warning banner (`Chưa thể kết luận bệnh nhân không có tiền sử`) with retry rather than assuming zero allergies.
   - **CRITICAL Allergy Interception**: Prescribing a medicine matching a `CRITICAL` allergy (or Beta-lactam/Penicillin cross-reactivity) MUST trigger a confirmation modal before adding the item.
   - **Prescription Sign Guard**: Completing a consultation with unconfirmed draft prescription items or pending voice drafts is strictly blocked until the doctor confirms or clears the draft.

---

## 2. Phase 4.1 — Consultation Workspace Decomposition (`consultation/[id]/page.tsx`)

### Problem Statement
`src/app/(dashboard)/consultation/[id]/page.tsx` grew to **2,613 lines** with 60+ `useState` declarations, mixing catalog constants, vitals threshold logic, 6 clinical tabs, prescription right-panel + drawer + modals, and AI RAG / Voice STT integration inside a single component.

### Modular Target Architecture
1. **Domain Constants & Helpers (`src/components/consultation/consultation-constants.ts`)**:
   - `ICD10_CATALOG`, `CONSULTATION_STATUS_LABELS`, vital sign threshold evaluators (`getTempWarning`, `getBpWarning`, `getHeartRateWarning`, `getSpo2Warning`), and demo fallback consultation builder.
2. **Sub-Components (`src/components/consultation/`)**:
   - `PatientHeaderBar.tsx`: Patient demographics header, consultation/prescription status badges, inherited handover draft banner, allergy notices (Level 1 CRITICAL / Level 2 WARNING / Load Error retry), and status banners (`LOCKED`, `AWAITING_CLS`, `AWAITING_REVIEW`).
   - `VitalsTab.tsx`: Tab 1 — Real-time vital signs inputs with threshold warning badges (`temperature`, `bloodPressure`, `heartRate`, `spo2`, `height`, `weight`).
   - `ClinicalExamTab.tsx`: Tab 2 — Symptoms (`symptoms`) and clinical examination notes (`clinicalNotes`).
   - `DiagnosisTab.tsx`: Tab 3 — Searchable ICD-10 combobox with outside-click dismiss, selected ICD-10 code/name display, and detailed clinical diagnosis textarea.
   - `LabOrdersTab.tsx`: Tab 4 — Lab service picker (`HandoverPicker`), selected service chips, clinical note for lab technician, existing lab orders list, CLS synchronization (`synchronizeReview`), and doctor result acknowledgment (`markReviewed`).
   - `EmrSummaryTab.tsx`: Tab 5 — Uploaded health records, follow-up appointment panel, EMR master card, past consultations list, and past prescriptions list with 3-state loading/error handling.
   - `AiClinicalTab.tsx`: Tab 6 — Hybrid RAG query bar, quick prompt chips, DDI warning alert, formatted markdown synthesis, citations (`ClinicalCitations`), and one-click diagnosis suggestion application.
   - `PrescriptionRightPanel.tsx`: Right column (2/5) — Voice prescription wrapper (`VoicePrescriptionPanel`), prescription items preview (`VoicePrescriptionPreview`), manual review (`NamedMedicineReview`), progressive disclosure notes, and follow-up scheduling (`FollowUpFields`).
   - `MedicineDrawerModal.tsx`: Slide-over portal drawer for searching/selecting catalog medicines, configuring dosage/frequency/timing/duration/quantity, and `PrescriptionDetailsEditor`.
   - `HistoryDetailModals.tsx`: Read-only detail modals for inspecting past consultations (`selectedHistoryConsultation`) and past prescriptions (`selectedHistoryPrescription`).
3. **Orchestrator Page (`src/app/(dashboard)/consultation/[id]/page.tsx`)**:
   - Coordinates consultation/patient/prescription/lab state, voice STT extraction preview, sticky bottom action bar, and renders the modular sub-components cleanly.

---

## 3. Eraser.io Architecture Diagram DSL

```
title Doctor Consultation Workspace Architecture (Phase 4.1)

ConsultationPage [icon: monitor, color: purple] {
  PatientHeaderBar [icon: user, label: "Demographics, Allergies & Status Banners"]
  LeftClinicalTabs [icon: layers, label: "6 Clinical Tabs (3/5 Grid)"]
  PrescriptionRightPanel [icon: file-text, label: "Prescription & Follow-Up (2/5 Grid)"]
  MedicineDrawerModal [icon: sidebar, label: "Portal Slide-Over Medicine Drawer"]
  HistoryDetailModals [icon: archive, label: "Past Visit & Prescription Modals"]
}

LeftClinicalTabs -> VitalsTab: "1. Sinh hiệu"
LeftClinicalTabs -> ClinicalExamTab: "2. Triệu chứng & Lâm sàng"
LeftClinicalTabs -> DiagnosisTab: "3. Chẩn đoán ICD-10"
LeftClinicalTabs -> LabOrdersTab: "4. Chỉ định Cận lâm sàng"
LeftClinicalTabs -> EmrSummaryTab: "5. Tóm tắt lâm sàng"
LeftClinicalTabs -> AiClinicalTab: "6. Trợ lý AI Lâm sàng (RAG)"
```


---

## 4. Phase 4.2 — Queue & Reception Screens Decomposition

### Problem Statement
`staff/checkin/page.tsx` (511 lines), `dashboard/queue/page.tsx` (472 lines), `lab/queue/page.tsx` (436 lines), and `staff/pharmacy/page.tsx` (344 lines) duplicate status/priority badge definitions, visibility-aware polling logic, AI voice notification dispatchers (`careflow:ai-notify`), and inline table/form rendering.

### Modular Target Architecture (`src/components/queue/`)
1. **`QueueBadges.tsx`**:
   - Unified `QueueStatusDot`, `ReceptionStatusText`, `QueuePriorityBadge`, `PRIORITY_LABELS`, and `pharmacyStatusLabel`.
2. **`useQueueOperations.ts`**:
   - Visibility-aware interval polling (`useVisibilityPolling`), AI voice notification dispatcher (`notifyAi`), and common queue transition helpers.
3. **Sub-Components**:
   - `HospitalQrCheckInCard.tsx`: Hospital-wide dynamic QR display, clipboard copy, emulator token textarea, and manual counter check-in form + latest result panel (`staff/checkin`).
   - `ReceptionQueueTable.tsx`: Daily room reception table with 3-state loading/error/empty handling and per-entry check-in/recall/miss actions (`staff/checkin`).
   - `LabResultFormPanel.tsx`: Active lab order result entry form and finalization (`lab/queue`).
   - `PharmacyVerificationPanel.tsx`: Patient-to-prescription verification and FIFO dispensing panel (`staff/pharmacy`).


---

## 5. Phase 4.3 — Admin Facility & General Dashboard Decomposition (`admin/facility/page.tsx` & `dashboard/general/page.tsx`)

### 5.1 Target Shared & Sub-Components
1. **Admin Facility Components (`src/components/admin/`)**:
   - `FacilitySelectBox.tsx`: Custom searchable dropdown combobox (`useRef` + `mousedown` outside-click listener, `rounded-xl`, `shadow-lg`, SJD purple hover) replacing native HTML `<select>` controls in facility modals per CareFlow UI rules.
   - `FacilityColumnsGrid.tsx`: 3-column directory grid (Departments, Rooms & Service Points, Doctor Profiles) with 3-state skeleton/empty/data rendering.
   - `DepartmentFormModal.tsx`: Modal for creating/editing hospital departments with local form state.
   - `RoomFormModal.tsx`: Modal for creating/editing clinical/lab/imaging/pharmacy rooms using `FacilitySelectBox` for department and service point type selection.
   - `DoctorProfileFormModal.tsx`: Modal for creating/editing doctor profiles using `FacilitySelectBox` for department and room assignment.
2. **General Dashboard Components (`src/components/dashboard/`)**:
   - `GeneralStatCards.tsx`: Role-aware stat cards (Waiting count, Completed count, Role queue shortcut).
   - `GeneralQueuePanel.tsx`: Active queue table with search filter, `QueueStatusDot`, per-row call/start action, and "Gọi bệnh nhân tiếp theo" footer.
   - `RecentlyCompletedPanel.tsx`: Right-hand panel listing today's completed consultations with 3-state loading/empty/list rendering.
