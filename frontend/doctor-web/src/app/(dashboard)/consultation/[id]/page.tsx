"use client";

import { useEffect, useState, use, useCallback, useRef } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import { consultationApi, ConsultationResponse } from "@/lib/consultation-api";
import {
  prescriptionApi,
  PrescriptionResponse,
  DEFAULT_MEDICINE_CATALOG,
} from "@/lib/prescription-api";
import { patientApi, emrApi, PatientAllergyResponse } from "@/lib/patient-api";
import { getErrorMessage } from "@/lib/error-utils";
import { labApi, LabCatalogItem, LabOrderResponse } from "@/lib/lab-api";
import { queueApi } from "@/lib/queue-api";
import { patientAge as ageFromBirthday } from "@/lib/patient-display";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import VoiceRecorderButton from "@/components/VoiceRecorderButton";
import VoicePreviewModal from "@/components/VoicePreviewModal";
import { buildDemoFallbackData } from "@/components/consultation/consultation-constants";
import PatientHeaderBar from "@/components/consultation/PatientHeaderBar";
import VitalsTab from "@/components/consultation/VitalsTab";
import ClinicalExamTab from "@/components/consultation/ClinicalExamTab";
import DiagnosisTab from "@/components/consultation/DiagnosisTab";
import LabOrdersTab from "@/components/consultation/LabOrdersTab";
import EmrSummaryTab from "@/components/consultation/EmrSummaryTab";
import AiClinicalTab from "@/components/consultation/AiClinicalTab";
import PrescriptionRightPanel from "@/components/consultation/PrescriptionRightPanel";
import MedicineDrawerModal from "@/components/consultation/MedicineDrawerModal";
import HistoryDetailModals from "@/components/consultation/HistoryDetailModals";
import ConsultationActionBar from "@/components/consultation/ConsultationActionBar";
import { useClinicalAi } from "@/components/consultation/useClinicalAi";
import { usePrescriptionForm } from "@/components/consultation/usePrescriptionForm";

export default function ConsultationPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = use(params);
  const consultationId = resolvedParams.id;
  const router = useRouter();
  const { user } = useAuth();

  const [activeTab, setActiveTab] = useState<"vitals" | "clinical" | "diagnosis" | "labs" | "summary" | "ai">("vitals");

  // Lab Orders state
  const [labCatalog, setLabCatalog] = useState<LabCatalogItem[]>([]);
  const [selectedLabServices, setSelectedLabServices] = useState<LabCatalogItem[]>([]);
  const [labClinicalNote, setLabClinicalNote] = useState("");
  const [existingLabOrders, setExistingLabOrders] = useState<LabOrderResponse[]>([]);
  const [isSubmittingLabOrder, setIsSubmittingLabOrder] = useState(false);

  // Allergy banner eager-load state
  const [allergyNotes, setAllergyNotes] = useState<string | null>(null);
  const [patientAllergies, setPatientAllergies] = useState<PatientAllergyResponse[]>([]);
  const [allergyLoaded, setAllergyLoaded] = useState(false);

  // Clinical Summary lazy-load state
  const [summaryLoaded, setSummaryLoaded] = useState(false);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [consultationHistory, setConsultationHistory] = useState<ConsultationResponse[] | null>(null);
  const [prescriptionHistory, setPrescriptionHistory] = useState<PrescriptionResponse[] | null>(null);
  const [patientMedicalHistory, setPatientMedicalHistory] = useState<string | null>(null);
  const [emrRecord, setEmrRecord] = useState<{ recordNumber: string; bloodType: string; medicalHistory: string } | null>(null);

  // Detail Modal view states
  const [selectedHistoryConsultation, setSelectedHistoryConsultation] = useState<ConsultationResponse | null>(null);
  const [selectedHistoryPrescription, setSelectedHistoryPrescription] = useState<PrescriptionResponse | null>(null);

  // Custom Modal confirm state
  const [confirmModalConfig, setConfirmModalConfig] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    variant?: "primary" | "warning" | "danger" | "purple";
    showItemList?: boolean;
    onConfirm: () => void;
  }>({
    isOpen: false,
    title: "",
    message: "",
    onConfirm: () => {},
  });

  // Consultation states
  const [consultation, setConsultation] = useState<ConsultationResponse | null>(null);
  const [patientName, setPatientName] = useState("Bệnh nhân");
  const [patientAge, setPatientAge] = useState<number | null>(null);
  const [patientGender, setPatientGender] = useState("Chưa ghi nhận giới tính");
  const [patientIdCode, setPatientIdCode] = useState("");
  const [patientDataError, setPatientDataError] = useState<string | null>(null);
  const patientRequest = useRef(0);

  // Vital signs & Clinical states
  const [temperature, setTemperature] = useState("");
  const [bloodPressure, setBloodPressure] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [spo2, setSpo2] = useState("");
  const [height, setHeight] = useState("");
  const [weight, setWeight] = useState("");
  const [symptoms, setSymptoms] = useState("");
  const [clinicalNotes, setClinicalNotes] = useState("");
  const [icd10Code, setIcd10Code] = useState("");
  const [icd10Name, setIcd10Name] = useState("");
  const [diagnosis, setDiagnosis] = useState("");

  const [completionPending, setCompletionPending] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);

  const showToast = useCallback((message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    setTimeout(() => {
      setToast(null);
    }, 3000);
  }, []);

  const {
    voiceDraft,
    medDetails,
    setMedDetails,
    prescriptionId,
    setPrescriptionId,
    prescriptionItems,
    setPrescriptionItems,
    editPrescriptionItems,
    prescriptionNotes,
    setPrescriptionNotes,
    followUpDate,
    setFollowUpDate,
    followUpDepartment,
    setFollowUpDepartment,
    followUpTimeSlot,
    setFollowUpTimeSlot,
    followUpDelivery,
    setFollowUpDelivery,
    prescriptionStatus,
    setPrescriptionStatus,
    inheritedDrafts,
    setInheritedDrafts,
    showPrescriptionNotes,
    setShowPrescriptionNotes,
    showMedNotesInput,
    setShowMedNotesInput,
    isMedicineDrawerOpen,
    setIsMedicineDrawerOpen,
    editingMedicineIndex,
    medicines,
    setMedicines,
    medSearchQuery,
    setMedSearchQuery,
    showMedDropdown,
    setShowMedDropdown,
    selectedMedicine,
    setSelectedMedicine,
    medDosage,
    setMedDosage,
    medFrequency,
    setMedFrequency,
    medTiming,
    setMedTiming,
    medDuration,
    setMedDuration,
    medQuantity,
    setMedQuantity,
    medNotes,
    setMedNotes,
    openAddMedicineDrawer,
    openEditMedicineDrawer,
    saveMedicineToPrescription,
    removeMedicine,
    clearAllMedicines,
    saveDraft,
    confirmPrescription,
  } = usePrescriptionForm({
    consultationId,
    userId: user?.id,
    consultation,
    patientAllergies,
    temperature,
    bloodPressure,
    heartRate,
    spo2,
    height,
    weight,
    symptoms,
    clinicalNotes,
    icd10Code,
    icd10Name,
    diagnosis,
    isSaving,
    setIsSaving,
    showToast,
    setConfirmModalConfig,
  });

  const {
    aiLoading,
    aiResponse,
    aiQueryInput,
    setAiQueryInput,
    handleGetAiSuggestions,
    voiceTranscript,
    voiceExtractedFields,
    isVoicePreviewOpen,
    closeVoicePreview,
    handleVoiceRecorded,
    applyVoiceFields,
  } = useClinicalAi({
    consultationId,
    symptoms,
    diagnosis,
    clinicalNotes,
    patientName,
    patientGender,
    patientAge,
    patientAllergies,
    patientMedicalHistory,
    bloodPressure,
    heartRate,
    temperature,
    prescriptionItems,
    showToast,
    setHeartRate,
    setBloodPressure,
    setTemperature,
    setSpo2,
    setHeight,
    setWeight,
    setSymptoms,
    setClinicalNotes,
    setDiagnosis,
    setIcd10Code,
    setIcd10Name,
  });

  const setupDemoFallbackConsultation = useCallback(() => {
    const demo = buildDemoFallbackData(consultationId);
    setConsultation(demo.consultation);
    setPatientName(demo.patientName);
    setPatientAge(demo.patientAge);
    setPatientGender(demo.patientGender);
    setPatientIdCode(demo.patientIdCode);
    setPatientAllergies(demo.patientAllergies);
    setPatientMedicalHistory(demo.patientMedicalHistory);
    setAllergyLoaded(true);

    setTemperature("38.5");
    setBloodPressure("145/95");
    setHeartRate("88");
    setSpo2("96");
    setHeight("168");
    setWeight("65");
    setSymptoms(demo.consultation.symptoms!);
    setClinicalNotes(demo.consultation.clinicalNotes!);
    setIcd10Code(demo.consultation.icd10Code!);
    setIcd10Name(demo.consultation.icd10Name!);
    setDiagnosis(demo.consultation.diagnosis!);

    setMedicines(DEFAULT_MEDICINE_CATALOG);
    setPrescriptionItems(demo.prescriptionItems);
    setShowPrescriptionNotes(true);
    setPrescriptionNotes(demo.prescriptionNotes);
  }, [
    consultationId,
    setMedicines,
    setPrescriptionItems,
    setPrescriptionNotes,
    setShowPrescriptionNotes,
  ]);

  const loadPatientDetails = useCallback(async (patientId: string) => {
    const version = ++patientRequest.current;
    setPatientDataError(null);
    setAllergyLoaded(false);
    try {
      const response = await patientApi.getPatientById(patientId);
      if (version !== patientRequest.current) return;
      const data = response.data;
      if (!data || data.id !== patientId) throw new Error("Thông tin hồ sơ không khớp ca khám.");
      setPatientName(data.fullName || `Bệnh nhân (${patientId.slice(0, 8)})`);
      setPatientIdCode(data.id || patientId);
      setPatientAge(ageFromBirthday(data.dateOfBirth));
      setPatientGender(
        data.gender === "FEMALE"
          ? "Nữ"
          : data.gender === "MALE"
            ? "Nam"
            : data.gender === "OTHER"
              ? "Khác"
              : "Chưa ghi nhận giới tính"
      );
      setAllergyNotes(data.allergyNotes || null);
      setPatientAllergies(data.allergies || []);
      setPatientMedicalHistory(data.medicalHistory || null);
      setAllergyLoaded(true);
    } catch (failure) {
      if (version !== patientRequest.current) return;
      setPatientDataError(
        getErrorMessage(
          failure,
          "Không tải được thông tin hồ sơ và dị ứng. Chưa thể kết luận bệnh nhân không có tiền sử."
        )
      );
    }
  }, []);

  useEffect(() => {
    async function loadData() {
      try {
        setIsLoading(true);
        if (consultationId === "demo" || consultationId === "demo-01") {
          setupDemoFallbackConsultation();
          showToast("Đã kích hoạt Ca Khám Mẫu Demo sẵn sàng thử nghiệm Trợ lý AI RAG", "success");
          return;
        }

        setLoadError(null);
        setConsultation(null);
        ++patientRequest.current;
        setSummaryLoaded(false);
        setSummaryError(null);
        setConsultationHistory(null);
        setPrescriptionHistory(null);
        setEmrRecord(null);
        setPatientAge(null);
        setPatientGender("Chưa ghi nhận giới tính");
        setPatientMedicalHistory(null);
        setAllergyLoaded(false);
        setPatientAllergies([]);
        setAllergyNotes(null);
        setPatientDataError(null);

        const consRes = await consultationApi.getConsultation(consultationId);
        const consData = consRes.data;
        setConsultation(consData);
        const shortPatientId = consData.patientId ? consData.patientId.slice(0, 8) : "";
        setPatientIdCode(shortPatientId);

        await loadPatientDetails(consData.patientId);

        setTemperature(consData.temperature?.toString() || "");
        setBloodPressure(consData.bloodPressure || "");
        setHeartRate(consData.heartRate?.toString() || "");
        setSpo2(consData.spo2?.toString() || "");
        setHeight(consData.height?.toString() || "");
        setWeight(consData.weight?.toString() || "");
        setSymptoms(consData.symptoms || "");
        setClinicalNotes(consData.clinicalNotes || "");
        setIcd10Code(consData.icd10Code || "");
        setIcd10Name(consData.icd10Name || "");
        setDiagnosis(consData.diagnosis || "");

        const catalogRes = await labApi.getCatalog();
        setLabCatalog(catalogRes.data);

        try {
          const medRes = await prescriptionApi.getMedicineCatalog();
          if (medRes.data && medRes.data.length > 0) {
            setMedicines(medRes.data);
          } else {
            setMedicines(DEFAULT_MEDICINE_CATALOG);
          }
        } catch {
          setMedicines(DEFAULT_MEDICINE_CATALOG);
        }

        const prescRes = await prescriptionApi.getByConsultation(consultationId);
        if (prescRes.data && prescRes.data.length > 0) {
          setInheritedDrafts(prescRes.data.filter((p) => p.status === "DRAFT" && p.doctorId !== user?.id));
          const presc =
            prescRes.data.find(
              (p) =>
                p.doctorId === user?.id &&
                p.status !== "CANCELLED" &&
                p.status !== "CANCELLED_BY_AMENDMENT"
            ) || prescRes.data.find((p) => p.status === "CONFIRMED" || p.status === "DISPENSED");
          if (presc) {
            setPrescriptionId(presc.id);
            setPrescriptionStatus(presc.status);
            setPrescriptionNotes(presc.notes || "");
            if (presc.notes) setShowPrescriptionNotes(true);
            setFollowUpDate(presc.followUpDate || "");
            setFollowUpDepartment(presc.followUpDepartment || "");
            setFollowUpTimeSlot(presc.followUpTimeSlot || "");
            setFollowUpDelivery(presc);
            setPrescriptionItems(
              presc.items.map((item) => ({
                medicineName: item.medicineName,
                medicineCode: item.medicineCode,
                unit: item.unit,
                dosage: item.dosage,
                frequency: item.frequency,
                timing: item.timing,
                duration: item.duration,
                quantity: item.quantity,
                notes: item.notes,
                details: item.details,
              }))
            );
          }
        }

        try {
          const labRes = await labApi.getByConsultation(consultationId);
          if (labRes.data) {
            setExistingLabOrders(labRes.data);
          }
        } catch (failure) {
          throw new Error(
            getErrorMessage(
              failure,
              "Chưa tải được chỉ định xét nghiệm; không thể kết luận ca không có chỉ định."
            )
          );
        }
      } catch (err) {
        if (consultationId.startsWith("demo")) {
          console.warn("Consultation service offline. Falling back to Demo Consultation Data:", err);
          setupDemoFallbackConsultation();
          showToast(
            "Backend lâm sàng chưa bật. Đã tự động nạp Ca Khám Mẫu để thử nghiệm AI RAG",
            "warning"
          );
        } else {
          console.error(err);
          setLoadError(err instanceof Error ? err.message : "Không thể tải thông tin phiên khám.");
        }
      } finally {
        setIsLoading(false);
      }
    }
    void loadData();
  }, [
    consultationId,
    user?.id,
    loadPatientDetails,
    setupDemoFallbackConsultation,
    showToast,
    setMedicines,
    setInheritedDrafts,
    setPrescriptionId,
    setPrescriptionStatus,
    setPrescriptionNotes,
    setShowPrescriptionNotes,
    setFollowUpDate,
    setFollowUpDepartment,
    setFollowUpTimeSlot,
    setFollowUpDelivery,
    setPrescriptionItems,
  ]);

  const summaryPatientId = consultation?.patientId;
  useEffect(() => {
    if (activeTab !== "summary" || summaryLoaded || !summaryPatientId) return;
    const patientId = summaryPatientId;
    let active = true;
    async function loadSummary() {
      setSummaryLoading(true);
      setSummaryError(null);
      try {
        const [consRes, prescRes, emrRes] = await Promise.all([
          consultationApi.getByPatient(patientId),
          prescriptionApi.getByPatient(patientId),
          emrApi.getPatientSummary(patientId),
        ]);
        if (!active) return;
        if (!Array.isArray(consRes.data) || !Array.isArray(prescRes.data) || !emrRes.data) {
          throw new Error("Máy chủ chưa trả đủ dữ liệu bệnh sử.");
        }
        setConsultationHistory(consRes.data.filter((item) => item.id !== consultationId));
        setPrescriptionHistory(prescRes.data);
        setEmrRecord(emrRes.data.medicalRecord || null);
      } catch (failure) {
        if (active) {
          setSummaryError(
            getErrorMessage(failure, "Không tải được bệnh sử. Chưa thể kết luận không có dữ liệu.")
          );
        }
      } finally {
        if (active) {
          setSummaryLoading(false);
          setSummaryLoaded(true);
        }
      }
    }
    void loadSummary();
    return () => {
      active = false;
    };
  }, [activeTab, summaryLoaded, summaryPatientId, consultationId]);

  const executeWaitForResults = async () => {
    if (!consultation) return;
    await consultationApi.waitForResults(consultationId);
    setConsultation((current) => (current ? { ...current, status: "AWAITING_CLS" } : current));
    let initial = consultation.queueEntryId;
    if (!initial && consultation.appointmentId) {
      initial = (await queueApi.getTicket(consultation.appointmentId)).data.ticketId;
    }
    if (!initial) {
      throw new Error(
        "Đã chuyển chờ CLS nhưng chưa xác định được lượt để đóng. Tải lại ca khám rồi thử tiếp tục chờ CLS."
      );
    }
    await queueApi.completeEntry(initial);
  };

  const executeCompleteConsultation = async () => {
    setIsSaving(true);
    try {
      if (consultation?.status !== "COMPLETED" && !(await saveDraft())) return;
      setIsSaving(true);
      const result = (await consultationApi.finalizeVisit(consultationId)).data;
      setConsultation(result.consultation);
      setCompletionPending(!result.complete);
      if (!result.complete) {
        showToast(
          "Ca khám đã đóng; hàng đợi hoặc lịch hẹn chưa đồng bộ xong. Bấm Thử lại đồng bộ để tiếp tục.",
          "warning"
        );
        return;
      }
      showToast("Ca khám, hàng đợi và lịch hẹn đã hoàn tất.");
      router.replace("/dashboard/general");
    } catch (failure) {
      showToast(
        getErrorMessage(failure, "Chưa hoàn tất thao tác. Tải lại trạng thái trước khi thử lại."),
        "danger"
      );
    } finally {
      setIsSaving(false);
      setConfirmModalConfig((previous) => ({ ...previous, isOpen: false }));
    }
  };

  const completeConsultation = () => {
    if (voiceDraft.current?.pending()) {
      voiceDraft.current.open();
      showToast(
        "Lưu toa nháp và kiểm tra các dòng thuốc trước khi ký và hoàn tất phiên khám.",
        "warning"
      );
      return;
    }
    if (
      prescriptionItems.length > 0 &&
      prescriptionStatus !== "CONFIRMED" &&
      prescriptionStatus !== "DISPENSED"
    ) {
      showToast("Vui lòng KÝ và XÁC NHẬN đơn thuốc trước khi hoàn tất phiên khám.", "warning");
      if (typeof window !== "undefined") {
        window.dispatchEvent(
          new CustomEvent("careflow:ai-notify", {
            detail: {
              text: "Bác sĩ ơi, đơn thuốc đang ở dạng Nháp. Bác sĩ vui lòng nhấn KÝ & XÁC NHẬN ĐƠN trước khi hoàn tất ca khám nha!",
            },
          })
        );
      }
      return;
    }

    setConfirmModalConfig({
      isOpen: true,
      title: "Hoàn tất Phiên khám",
      message:
        "Bác sĩ có chắc chắn muốn HOÀN TẤT phiên khám lâm sàng cho bệnh nhân? Hồ sơ bệnh án sẽ được đóng và lưu trữ.",
      variant: "purple",
      onConfirm: executeCompleteConsultation,
    });
  };

  if (isLoading) {
    return (
      <div className="flex h-[calc(100vh-200px)] items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (loadError || !consultation) {
    return (
      <section role="alert" className="max-w-xl mx-auto mt-12 bg-white border border-red-200 rounded-xl p-6 space-y-4">
        <h1 className="text-xl font-bold">Không thể mở phiên khám</h1>
        <p>{loadError || "Không tìm thấy thông tin phiên khám."}</p>
        <p className="text-sm text-gray-600">
          Nếu đây là ca bàn giao, bạn cần xác nhận tiếp nhận trước khi mở ca khám.
        </p>
        <div className="flex gap-3">
          <Link href="/dashboard/handovers" className="border rounded-lg px-4 py-2">
            Quay lại bàn giao
          </Link>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="bg-purple-800 text-white rounded-lg px-4 py-2"
          >
            Thử lại
          </button>
        </div>
      </section>
    );
  }

  const isConsultationLocked = consultation.status === "COMPLETED" || consultation.status === "CANCELLED";
  const isPrescriptionLocked =
    prescriptionStatus === "CONFIRMED" || prescriptionStatus === "DISPENSED" || isConsultationLocked;

  return (
    <div className="space-y-6 pb-40 relative bg-[linear-gradient(to_right,rgba(43,29,48,0.015)_1px,transparent_1px),linear-gradient(to_bottom,rgba(43,29,48,0.015)_1px,transparent_1px)] bg-[size:24px_24px]">
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 p-4 border shadow-lg max-w-md transition-all rounded-none ${
            toast.type === "success"
              ? "bg-[#2B1D30] text-white border-primary-500"
              : toast.type === "warning"
                ? "bg-amber-900 text-white border-amber-500"
                : "bg-rose-900 text-white border-rose-500"
          }`}
        >
          <div className="flex items-center justify-between gap-4">
            <span className="text-xs font-semibold">{toast.message}</span>
            <button
              type="button"
              onClick={() => setToast(null)}
              className="text-white/60 hover:text-white text-xs font-bold"
            >
              X
            </button>
          </div>
        </div>
      )}

      <PatientHeaderBar
        consultation={consultation}
        patientName={patientName}
        patientAge={patientAge}
        patientGender={patientGender}
        patientIdCode={patientIdCode}
        prescriptionStatus={prescriptionStatus}
        inheritedDrafts={inheritedDrafts}
        onSelectInheritedDraft={setSelectedHistoryPrescription}
        patientDataError={patientDataError}
        onRetryPatientDetails={() => void loadPatientDetails(consultation.patientId)}
        allergyLoaded={allergyLoaded}
        patientAllergies={patientAllergies}
        allergyNotes={allergyNotes}
        isConsultationLocked={isConsultationLocked}
        onNavigateDashboard={() => router.push("/dashboard")}
        onResumeReview={async () => {
          try {
            await consultationApi.resume(consultationId);
            setConsultation((prev) => (prev ? { ...prev, status: "IN_PROGRESS" } : null));
            showToast("Đã chuyển sang đọc kết quả và tiếp tục phiên khám.", "success");
          } catch {
            showToast("Không thể cập nhật trạng thái phiên khám.", "danger");
          }
        }}
      />

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <div className="lg:col-span-3 space-y-3">
          {!isConsultationLocked && (
            <div className="flex items-center justify-end">
              <VoiceRecorderButton onAudioReady={handleVoiceRecorded} disabled={isConsultationLocked} />
            </div>
          )}

          <div className="border-b border-card-border flex items-center overflow-x-auto bg-card-bg rounded-t-lg">
            <button
              type="button"
              onClick={() => setActiveTab("vitals")}
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all rounded-tl-lg ${
                activeTab === "vitals"
                  ? "border-primary-600 text-primary-600 bg-[#F8F6F9]"
                  : "border-transparent text-[#6A5C70] hover:text-[#2B1D30]"
              }`}
            >
              1. Sinh hiệu
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("clinical")}
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all ${
                activeTab === "clinical"
                  ? "border-primary-600 text-primary-600 bg-[#F8F6F9]"
                  : "border-transparent text-[#6A5C70] hover:text-[#2B1D30]"
              }`}
            >
              2. Triệu chứng &amp; Lâm sàng
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("diagnosis")}
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all ${
                activeTab === "diagnosis"
                  ? "border-primary-600 text-primary-600 bg-[#F8F6F9]"
                  : "border-transparent text-[#6A5C70] hover:text-[#2B1D30]"
              }`}
            >
              3. Chẩn đoán ICD-10
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("labs")}
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all ${
                activeTab === "labs"
                  ? "border-primary-600 text-primary-600 bg-[#F8F6F9]"
                  : "border-transparent text-[#6A5C70] hover:text-[#2B1D30]"
              }`}
            >
              4. Chỉ định Cận lâm sàng ({selectedLabServices.length > 0 ? selectedLabServices.length : existingLabOrders.length})
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("summary")}
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all ${
                activeTab === "summary"
                  ? "border-primary-600 text-primary-600 bg-[#F8F6F9]"
                  : "border-transparent text-[#6A5C70] hover:text-[#2B1D30]"
              }`}
            >
              5. Tóm tắt lâm sàng
            </button>
            <button
              type="button"
              onClick={() => {
                setActiveTab("ai");
                if (!aiResponse) void handleGetAiSuggestions();
              }}
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all flex items-center gap-1.5 ${
                activeTab === "ai"
                  ? "border-[#6E2582] text-[#6E2582] bg-purple-50"
                  : "border-transparent text-purple-700 hover:text-[#6E2582]"
              }`}
            >
              <svg className="w-3.5 h-3.5 text-purple-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              <span>Trợ lý AI Lâm sàng</span>
              <span className="px-1.5 py-0.2 text-[9px] bg-purple-100 text-purple-800 rounded font-black border border-purple-200">
                RAG
              </span>
            </button>
          </div>

          {activeTab === "vitals" && (
            <VitalsTab
              temperature={temperature}
              setTemperature={setTemperature}
              bloodPressure={bloodPressure}
              setBloodPressure={setBloodPressure}
              heartRate={heartRate}
              setHeartRate={setHeartRate}
              spo2={spo2}
              setSpo2={setSpo2}
              height={height}
              setHeight={setHeight}
              weight={weight}
              setWeight={setWeight}
              isConsultationLocked={isConsultationLocked}
            />
          )}

          {activeTab === "clinical" && (
            <ClinicalExamTab
              symptoms={symptoms}
              setSymptoms={setSymptoms}
              clinicalNotes={clinicalNotes}
              setClinicalNotes={setClinicalNotes}
              isConsultationLocked={isConsultationLocked}
            />
          )}

          {activeTab === "diagnosis" && (
            <DiagnosisTab
              icd10Code={icd10Code}
              setIcd10Code={setIcd10Code}
              icd10Name={icd10Name}
              setIcd10Name={setIcd10Name}
              diagnosis={diagnosis}
              setDiagnosis={setDiagnosis}
              isConsultationLocked={isConsultationLocked}
            />
          )}

          {activeTab === "labs" && (
            <LabOrdersTab
              consultationId={consultationId}
              consultation={consultation}
              userId={user?.id}
              symptoms={symptoms}
              isConsultationLocked={isConsultationLocked}
              isSaving={isSaving}
              setIsSaving={setIsSaving}
              labCatalog={labCatalog}
              selectedLabServices={selectedLabServices}
              setSelectedLabServices={setSelectedLabServices}
              labClinicalNote={labClinicalNote}
              setLabClinicalNote={setLabClinicalNote}
              existingLabOrders={existingLabOrders}
              setExistingLabOrders={setExistingLabOrders}
              isSubmittingLabOrder={isSubmittingLabOrder}
              setIsSubmittingLabOrder={setIsSubmittingLabOrder}
              executeWaitForResults={executeWaitForResults}
              showToast={showToast}
            />
          )}

          {activeTab === "summary" && (
            <EmrSummaryTab
              consultationId={consultationId}
              consultation={consultation}
              userId={user?.id}
              summaryLoading={summaryLoading}
              summaryError={summaryError}
              onRetrySummary={() => setSummaryLoaded(false)}
              emrRecord={emrRecord}
              patientMedicalHistory={patientMedicalHistory}
              consultationHistory={consultationHistory}
              prescriptionHistory={prescriptionHistory}
              onSelectHistoryConsultation={setSelectedHistoryConsultation}
              onSelectHistoryPrescription={setSelectedHistoryPrescription}
            />
          )}

          {activeTab === "ai" && (
            <AiClinicalTab
              aiLoading={aiLoading}
              aiResponse={aiResponse}
              aiQueryInput={aiQueryInput}
              setAiQueryInput={setAiQueryInput}
              handleGetAiSuggestions={handleGetAiSuggestions}
              prescriptionItems={prescriptionItems}
              onApplyDiagnosisSuggestion={(text) => {
                setDiagnosis(text);
                showToast("Đã áp dụng gợi ý vào ô Chẩn đoán", "success");
              }}
            />
          )}
        </div>

        <PrescriptionRightPanel
          consultationId={consultationId}
          userId={user?.id}
          voiceDraftRef={voiceDraft}
          prescriptionId={prescriptionId}
          prescriptionStatus={prescriptionStatus}
          prescriptionItems={prescriptionItems}
          setPrescriptionItems={setPrescriptionItems}
          editPrescriptionItems={editPrescriptionItems}
          prescriptionNotes={prescriptionNotes}
          setPrescriptionNotes={setPrescriptionNotes}
          showPrescriptionNotes={showPrescriptionNotes}
          setShowPrescriptionNotes={setShowPrescriptionNotes}
          followUpDate={followUpDate}
          setFollowUpDate={setFollowUpDate}
          followUpDepartment={followUpDepartment}
          setFollowUpDepartment={setFollowUpDepartment}
          followUpTimeSlot={followUpTimeSlot}
          setFollowUpTimeSlot={setFollowUpTimeSlot}
          followUpDelivery={followUpDelivery}
          setFollowUpDelivery={setFollowUpDelivery}
          isConsultationLocked={isConsultationLocked}
          isPrescriptionLocked={isPrescriptionLocked}
          isSaving={isSaving}
          setIsSaving={setIsSaving}
          saveDraft={saveDraft}
          openAddMedicineDrawer={openAddMedicineDrawer}
          openEditMedicineDrawer={openEditMedicineDrawer}
          removeMedicine={removeMedicine}
          clearAllMedicines={clearAllMedicines}
          showToast={showToast}
        />
      </div>

      {typeof document !== "undefined" &&
        createPortal(
          <div className="relative z-[60]">
            <MedicineDrawerModal
              isOpen={isMedicineDrawerOpen}
              onClose={() => setIsMedicineDrawerOpen(false)}
              editingMedicineIndex={editingMedicineIndex}
              medicines={medicines}
              medSearchQuery={medSearchQuery}
              setMedSearchQuery={setMedSearchQuery}
              showMedDropdown={showMedDropdown}
              setShowMedDropdown={setShowMedDropdown}
              selectedMedicine={selectedMedicine}
              setSelectedMedicine={setSelectedMedicine}
              medDosage={medDosage}
              setMedDosage={setMedDosage}
              medFrequency={medFrequency}
              setMedFrequency={setMedFrequency}
              medTiming={medTiming}
              setMedTiming={setMedTiming}
              medDuration={medDuration}
              setMedDuration={setMedDuration}
              medQuantity={medQuantity}
              setMedQuantity={setMedQuantity}
              medDetails={medDetails}
              setMedDetails={setMedDetails}
              showMedNotesInput={showMedNotesInput}
              setShowMedNotesInput={setShowMedNotesInput}
              medNotes={medNotes}
              setMedNotes={setMedNotes}
              saveMedicineToPrescription={saveMedicineToPrescription}
            />

            <ConsultationActionBar
              consultation={consultation}
              userDisplayName={user?.fullName || user?.username || user?.email}
              isConsultationLocked={isConsultationLocked}
              isSaving={isSaving}
              completionPending={completionPending}
              prescriptionId={prescriptionId}
              prescriptionStatus={prescriptionStatus}
              prescriptionItems={prescriptionItems}
              saveDraft={saveDraft}
              confirmPrescription={confirmPrescription}
              completeConsultation={completeConsultation}
              executeCompleteConsultation={executeCompleteConsultation}
              onNavigateDashboard={() => router.replace("/dashboard")}
              confirmModalConfig={confirmModalConfig}
              onCancelConfirmModal={() => setConfirmModalConfig((prev) => ({ ...prev, isOpen: false }))}
            />

            <HistoryDetailModals
              selectedHistoryConsultation={selectedHistoryConsultation}
              setSelectedHistoryConsultation={setSelectedHistoryConsultation}
              selectedHistoryPrescription={selectedHistoryPrescription}
              setSelectedHistoryPrescription={setSelectedHistoryPrescription}
              consultationHistory={consultationHistory}
              prescriptionHistory={prescriptionHistory}
            />
          </div>,
          document.body
        )}

      {isVoicePreviewOpen && voiceExtractedFields && (
        <VoicePreviewModal
          transcript={voiceTranscript}
          fields={voiceExtractedFields}
          onApply={applyVoiceFields}
          onClose={closeVoicePreview}
        />
      )}
    </div>
  );
}
