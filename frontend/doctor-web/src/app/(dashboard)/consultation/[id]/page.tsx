"use client";

import { useEffect, useState, use } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { consultationApi, ConsultationResponse, UpdateConsultationRequest } from "@/lib/consultation-api";
import { appointmentApi } from "@/lib/appointment-api";
import { prescriptionApi, PrescriptionResponse, PrescriptionItemRequest, MedicineCatalogItem } from "@/lib/prescription-api";
import { patientApi, emrApi, PatientAllergyResponse } from "@/lib/patient-api";
import { labApi, MOCK_LAB_SERVICES, LabCatalogItem, LabOrderResponse } from "@/lib/lab-api";
import { queueApi } from "@/lib/queue-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ConfirmModal from "@/components/ConfirmModal";

const ICD10_CATALOG = [
  { code: "K21.9", name: "Bệnh trào ngược dạ dày - thực quản không có viêm thực quản" },
  { code: "I10", name: "Bệnh tăng huyết áp vô căn (nguyên phát)" },
  { code: "E11.9", name: "Bệnh đái tháo đường không phụ thuộc insulin không có biến chứng" },
  { code: "M17.9", name: "Thoái hóa khớp gối không xác định" },
  { code: "J00", name: "Viêm mũi họng cấp (cảm thường)" },
  { code: "J18.9", name: "Viêm phổi không xác định" },
  { code: "K29.7", name: "Viêm dạ dày không xác định" },
];

export default function ConsultationPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = use(params);
  const consultationId = resolvedParams.id;
  const router = useRouter();
  const searchParams = useSearchParams();
  const entryId = searchParams.get("entryId");
  const { user } = useAuth();

  // Active tab for clinical details
  const [activeTab, setActiveTab] = useState<"vitals" | "clinical" | "diagnosis" | "labs" | "summary">("vitals");

  // Lab Orders state
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
  const [patientAge, setPatientAge] = useState(30);
  const [patientGender, setPatientGender] = useState("Nam");
  
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
  
  // ICD10 Search state
  const [icdSearchQuery, setIcdSearchQuery] = useState("");
  const [showIcdDropdown, setShowIcdDropdown] = useState(false);

  // Prescription states
  const [prescriptionId, setPrescriptionId] = useState<string | null>(null);
  const [prescriptionItems, setPrescriptionItems] = useState<PrescriptionItemRequest[]>([]);
  const [prescriptionNotes, setPrescriptionNotes] = useState("");
  const [followUpDate, setFollowUpDate] = useState("");
  const [prescriptionStatus, setPrescriptionStatus] = useState<"DRAFT" | "CONFIRMED" | "DISPENSED" | null>(null);

  // Progressive disclosure notes state
  const [showPrescriptionNotes, setShowPrescriptionNotes] = useState(false);
  const [showMedNotesInput, setShowMedNotesInput] = useState(false);

  // Drawer & Autocomplete UI states for Medicine Form
  const [isMedicineDrawerOpen, setIsMedicineDrawerOpen] = useState(false);
  const [editingMedicineCode, setEditingMedicineCode] = useState<string | null>(null);

  const [medicines, setMedicines] = useState<MedicineCatalogItem[]>([]);
  const [medSearchQuery, setMedSearchQuery] = useState("");
  const [showMedDropdown, setShowMedDropdown] = useState(false);
  const [selectedMedicine, setSelectedMedicine] = useState<MedicineCatalogItem | null>(null);
  
  const [medDosage, setMedDosage] = useState("1 viên / lần");
  const [medFrequency, setMedFrequency] = useState("2 lần / ngày");
  const [medTiming, setMedTiming] = useState("Sau khi ăn");
  const [medDuration, setMedDuration] = useState("5");
  const [medQuantity, setMedQuantity] = useState("10");
  const [medNotes, setMedNotes] = useState("");

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);

  const showToast = (message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    setTimeout(() => {
      setToast(null);
    }, 3000);
  };

  // Vital Signs threshold evaluation helpers
  const getTempWarning = (val: string) => {
    const num = parseFloat(val);
    if (!num) return null;
    if (num > 38.5) return { text: "Sốt cao", level: "danger" };
    if (num > 37.5) return { text: "Sốt nhẹ", level: "warning" };
    if (num < 36.0) return { text: "Hạ thân nhiệt", level: "warning" };
    return null;
  };

  const getBpWarning = (val: string) => {
    if (!val) return null;
    const parts = val.split("/");
    if (parts.length === 2) {
      const sys = parseInt(parts[0]);
      const dia = parseInt(parts[1]);
      if (sys >= 140 || dia >= 90) return { text: "Huyết áp cao", level: "danger" };
      if (sys <= 90 || dia <= 60) return { text: "Huyết áp thấp", level: "warning" };
    }
    return null;
  };

  const getHeartRateWarning = (val: string) => {
    const num = parseInt(val);
    if (!num) return null;
    if (num > 100) return { text: "Nhịp tim nhanh", level: "warning" };
    if (num < 60) return { text: "Nhịp tim chậm", level: "warning" };
    return null;
  };

  const getSpo2Warning = (val: string) => {
    const num = parseInt(val);
    if (!num) return null;
    if (num < 95) return { text: "Cảnh báo thiếu Oxy", level: "danger" };
    return null;
  };

  // Fetch all initial data
  useEffect(() => {
    async function loadData() {
      try {
        setIsLoading(true);
        // Get consultation
        const consRes = await consultationApi.getConsultation(consultationId);
        const consData = consRes.data;
        setConsultation(consData);

        // Fetch real patient info from patient-service
        try {
          const pRes = await patientApi.getPatientById(consData.patientId);
          if (pRes.data) {
            setPatientName(pRes.data.fullName || "Bệnh nhân");
            if (pRes.data.dateOfBirth) {
              const birthYear = new Date(pRes.data.dateOfBirth).getFullYear();
              setPatientAge(new Date().getFullYear() - birthYear);
            }
            if (pRes.data.gender) {
              setPatientGender(pRes.data.gender === "FEMALE" ? "Nữ" : "Nam");
            }
            if (pRes.data.allergyNotes) {
              setAllergyNotes(pRes.data.allergyNotes);
            }
            if (pRes.data.allergies) {
              setPatientAllergies(pRes.data.allergies);
            }
            if (pRes.data.medicalHistory) {
              setPatientMedicalHistory(pRes.data.medicalHistory);
            }
          }
        } catch {
          setPatientName(`Bệnh nhân (ID: ${consData.patientId.slice(0, 8)})`);
        } finally {
          setAllergyLoaded(true);
        }

        // Prepopulate inputs
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

        // Fetch medicine catalog from real API
        const medRes = await prescriptionApi.getMedicineCatalog();
        setMedicines(medRes.data);

        // Fetch existing prescription if any
        const prescRes = await prescriptionApi.getByConsultation(consultationId);
        if (prescRes.data && prescRes.data.length > 0) {
          const presc = prescRes.data[0];
          setPrescriptionId(presc.id);
          setPrescriptionStatus(presc.status);
          setPrescriptionNotes(presc.notes || "");
          if (presc.notes) setShowPrescriptionNotes(true);
          setFollowUpDate(presc.followUpDate || "");
          setPrescriptionItems(presc.items.map(item => ({
            medicineName: item.medicineName,
            medicineCode: item.medicineCode,
            unit: item.unit,
            dosage: item.dosage,
            frequency: item.frequency,
            timing: item.timing,
            duration: item.duration,
            quantity: item.quantity,
            notes: item.notes
          })));
        }

        // Fetch existing lab orders if any
        try {
          const labRes = await labApi.getByConsultation(consultationId);
          if (labRes.data) {
            setExistingLabOrders(labRes.data);
          }
        } catch {
          // Silent catch for lab order fetch
        }
      } catch (err) {
        console.error(err);
        showToast(err instanceof Error ? err.message : "Không thể tải thông tin phiên khám.", "danger");
      } finally {
        setIsLoading(false);
      }
    }
    loadData();
  }, [consultationId]);

  // Lazy load Clinical Summary data when tab "summary" is active
  useEffect(() => {
    if (activeTab !== "summary" || summaryLoaded || !consultation?.patientId) return;
    async function loadSummary() {
      setSummaryLoading(true);
      try {
        const [consRes, prescRes, emrRes] = await Promise.all([
          consultationApi.getByPatient(consultation!.patientId),
          prescriptionApi.getByPatient(consultation!.patientId),
          emrApi.getPatientSummary(consultation!.patientId).catch(() => null),
        ]);
        setConsultationHistory((consRes.data ?? []).filter(c => c.id !== consultationId));
        setPrescriptionHistory(prescRes.data ?? []);
        if (emrRes?.data?.medicalRecord) {
          setEmrRecord(emrRes.data.medicalRecord);
        }
      } catch {
        setConsultationHistory([]);
        setPrescriptionHistory([]);
      } finally {
        setSummaryLoading(false);
        setSummaryLoaded(true);
      }
    }
    loadSummary();
  }, [activeTab, summaryLoaded, consultation?.patientId, consultationId]);

  // Handle ICD-10 Search
  const filteredIcd10 = ICD10_CATALOG.filter(item => 
    item.code.toLowerCase().includes(icdSearchQuery.toLowerCase()) ||
    item.name.toLowerCase().includes(icdSearchQuery.toLowerCase())
  );

  const selectIcd10 = (code: string, name: string) => {
    setIcd10Code(code);
    setIcd10Name(name);
    setDiagnosis(name);
    setIcdSearchQuery("");
    setShowIcdDropdown(false);
  };

  // Medicine catalog search filter
  const filteredMedicines = medicines.filter(m => 
    m.name.toLowerCase().includes(medSearchQuery.toLowerCase()) ||
    m.code.toLowerCase().includes(medSearchQuery.toLowerCase())
  );

  const openAddMedicineDrawer = () => {
    setEditingMedicineCode(null);
    setSelectedMedicine(null);
    setMedSearchQuery("");
    setMedDosage("1 viên / lần");
    setMedFrequency("2 lần / ngày");
    setMedTiming("Sau khi ăn");
    setMedDuration("5");
    setMedQuantity("10");
    setMedNotes("");
    setShowMedNotesInput(false);
    setIsMedicineDrawerOpen(true);
  };

  const openEditMedicineDrawer = (item: PrescriptionItemRequest) => {
    setEditingMedicineCode(item.medicineCode);
    const catalogItem = medicines.find(m => m.code === item.medicineCode) || {
      code: item.medicineCode,
      name: item.medicineName,
      unit: item.unit,
      description: ""
    };
    setSelectedMedicine(catalogItem);
    setMedSearchQuery(item.medicineName);
    setMedDosage(item.dosage);
    setMedFrequency(item.frequency);
    setMedTiming(item.timing);
    setMedDuration(item.duration.toString());
    setMedQuantity(item.quantity.toString());
    setMedNotes(item.notes || "");
    if (item.notes) setShowMedNotesInput(true);
    setIsMedicineDrawerOpen(true);
  };

  const executeSaveMedicine = (newItem: PrescriptionItemRequest) => {
    if (editingMedicineCode) {
      setPrescriptionItems(prescriptionItems.map(item => item.medicineCode === editingMedicineCode ? newItem : item));
      showToast(`Đã cập nhật thuốc ${newItem.medicineName}`);
    } else {
      if (prescriptionItems.some(item => item.medicineCode === newItem.medicineCode)) {
        setPrescriptionItems(prescriptionItems.map(item => item.medicineCode === newItem.medicineCode ? newItem : item));
        showToast(`Thuốc ${newItem.medicineName} đã có trong đơn. Hệ thống đã cập nhật lại số lượng và liều dùng mới.`, "warning");
        setIsMedicineDrawerOpen(false);
        return;
      }
      setPrescriptionItems([...prescriptionItems, newItem]);
      showToast(`Đã thêm ${newItem.medicineName} vào đơn thuốc`);
    }
    setIsMedicineDrawerOpen(false);
  };

  const saveMedicineToPrescription = () => {
    if (!selectedMedicine) {
      showToast("Vui lòng chọn thuốc từ danh sách", "warning");
      return;
    }

    const newItem: PrescriptionItemRequest = {
      medicineName: selectedMedicine.name,
      medicineCode: selectedMedicine.code,
      unit: selectedMedicine.unit,
      dosage: medDosage,
      frequency: medFrequency,
      timing: medTiming,
      duration: parseInt(medDuration) || 1,
      quantity: parseInt(medQuantity) || 1,
      notes: medNotes
    };

    // Check CRITICAL (Level 1) allergy conflict
    const criticalConflict = patientAllergies.find(a => 
      a.severity === "CRITICAL" && (
        selectedMedicine.name.toLowerCase().includes(a.allergyName.toLowerCase()) ||
        (a.allergyGroup && a.allergyGroup.toLowerCase() === "beta-lactam" && ["MED002", "MED014"].includes(selectedMedicine.code)) ||
        (a.allergyName.toLowerCase().includes("penicillin") && ["MED002", "MED014"].includes(selectedMedicine.code))
      )
    );

    if (criticalConflict) {
      setConfirmModalConfig({
        isOpen: true,
        title: "🚨 CẢNH BÁO NGUY HIỂM CAO - PHẢN VỆ Y KHOA",
        message: `Thuốc "${selectedMedicine.name}" chứa thành phần thuộc nhóm [${criticalConflict.allergyGroup || criticalConflict.allergyName}]. Bệnh nhân có tiền sử DỊ ỨNG NẶNG (${criticalConflict.reaction || 'Nguy cơ sốc phản vệ'}). Bác sĩ có chắc chắn muốn tiếp tục kê đơn thuốc này không?`,
        variant: "danger",
        onConfirm: () => {
          executeSaveMedicine(newItem);
          setConfirmModalConfig(prev => ({ ...prev, isOpen: false }));
        }
      });
      return;
    }

    executeSaveMedicine(newItem);
  };

  const removeMedicine = (code: string, name: string) => {
    const updatedItems = prescriptionItems.filter(item => item.medicineCode !== code);
    setPrescriptionItems(updatedItems);
    showToast(`Đã xóa ${name} khỏi đơn thuốc`, "warning");

    // If no medicines left in prescription, clear draft prescription state
    if (updatedItems.length === 0) {
      setPrescriptionId(null);
      setPrescriptionStatus(null);
    }
  };

  const clearAllMedicines = () => {
    setPrescriptionItems([]);
    setPrescriptionId(null);
    setPrescriptionStatus(null);
    showToast("Đã xóa toàn bộ đơn thuốc nháp", "warning");
  };

  // Save draft
  const saveDraft = async () => {
    if (!user) return;
    setIsSaving(true);

    try {
      const consultationUpdate: UpdateConsultationRequest = {
        temperature: parseFloat(temperature) || undefined,
        bloodPressure: bloodPressure || undefined,
        heartRate: parseInt(heartRate) || undefined,
        spo2: parseInt(spo2) || undefined,
        height: parseFloat(height) || undefined,
        weight: parseFloat(weight) || undefined,
        symptoms,
        clinicalNotes,
        icd10Code,
        icd10Name,
        diagnosis
      };
      await consultationApi.updateConsultation(consultationId, consultationUpdate);

      if (prescriptionItems.length > 0) {
        const prescriptionData = {
          consultationId,
          patientId: consultation!.patientId,
          doctorId: user.id,
          diagnosis,
          notes: prescriptionNotes,
          followUpDate: followUpDate || undefined,
          items: prescriptionItems
        };

        if (prescriptionId) {
          const res = await prescriptionApi.updatePrescription(prescriptionId, prescriptionData);
          setPrescriptionStatus(res.data.status);
        } else {
          const res = await prescriptionApi.createPrescription(prescriptionData);
          setPrescriptionId(res.data.id);
          setPrescriptionStatus(res.data.status);
        }
      }

      showToast("Lưu thông tin nháp thành công.");
    } catch (err) {
      console.error(err);
      showToast(err instanceof Error ? err.message : "Đã xảy ra lỗi khi lưu thông tin.", "danger");
    } finally {
      setIsSaving(false);
    }
  };

  // Action to execute confirm prescription
  const executeConfirmPrescription = async () => {
    if (!prescriptionId) return;
    setIsSaving(true);
    try {
      const res = await prescriptionApi.confirmPrescription(prescriptionId);
      setPrescriptionStatus(res.data.status);
      showToast("Ký và Xác nhận đơn thuốc thành công. Hệ thống đã đồng bộ sang Quầy thuốc.");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Đã xảy ra lỗi khi ký đơn thuốc.", "danger");
    } finally {
      setIsSaving(false);
      setConfirmModalConfig(prev => ({ ...prev, isOpen: false }));
    }
  };

  // Confirm Prescription trigger
  const confirmPrescription = () => {
    if (!prescriptionId) {
      showToast("Vui lòng thêm thuốc và lưu nháp đơn trước khi ký xác nhận.", "warning");
      return;
    }

    setConfirmModalConfig({
      isOpen: true,
      title: "Ký & Xác nhận Đơn thuốc",
      message: "Bác sĩ có chắc chắn muốn KÝ và XÁC NHẬN đơn thuốc này? Sau khi xác nhận, đơn thuốc sẽ được chuyển tới Quầy thuốc và không thể sửa đổi.",
      variant: "purple",
      showItemList: true,
      onConfirm: executeConfirmPrescription
    });
  };

  // Action to execute complete consultation
  const executeCompleteConsultation = async () => {
    setIsSaving(true);
    try {
      await saveDraft();
      await consultationApi.completeConsultation(consultationId);
      if (entryId) {
        try {
          await queueApi.completeEntry(entryId);
        } catch {
          /* Ignore secondary queue complete failure */
        }
      }
      if (consultation?.appointmentId) {
        try {
          await appointmentApi.updateStatus(consultation.appointmentId, "COMPLETED", "Hoàn tất khám bệnh");
        } catch {
          /* Ignore secondary sync failure */
        }
      }
      
      showToast("Hoàn tất lượt khám thành công. Đang chuyển về bảng điều khiển...");
      setTimeout(() => {
        router.replace("/dashboard/general");
      }, 1200);
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Đã xảy ra lỗi khi hoàn tất phiên khám.", "danger");
    } finally {
      setIsSaving(false);
      setConfirmModalConfig(prev => ({ ...prev, isOpen: false }));
    }
  };

  // Complete Consultation trigger
  const completeConsultation = () => {
    if (prescriptionId && prescriptionStatus !== "CONFIRMED") {
      showToast("Vui lòng KÝ và XÁC NHẬN đơn thuốc trước khi hoàn tất phiên khám.", "warning");
      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
          detail: {
            text: "Bác sĩ ơi, đơn thuốc đang ở dạng Nháp. Bác sĩ vui lòng nhấn KÝ & XÁC NHẬN ĐƠN trước khi hoàn tất ca khám nha!"
          }
        }));
      }
      return;
    }

    setConfirmModalConfig({
      isOpen: true,
      title: "Hoàn tất Phiên khám",
      message: "Bác sĩ có chắc chắn muốn HOÀN TẤT phiên khám lâm sàng cho bệnh nhân? Hồ sơ bệnh án sẽ được đóng và lưu trữ.",
      variant: "purple",
      onConfirm: executeCompleteConsultation
    });
  };

  if (isLoading) {
    return (
      <div className="flex h-[calc(100vh-200px)] items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  const isConsultationLocked = consultation?.status === "COMPLETED" || consultation?.status === "CANCELLED";
  const isPrescriptionLocked = prescriptionStatus === "CONFIRMED" || isConsultationLocked;

  const tempWarn = getTempWarning(temperature);
  const bpWarn = getBpWarning(bloodPressure);
  const hrWarn = getHeartRateWarning(heartRate);
  const spo2Warn = getSpo2Warning(spo2);

  return (
    <div className="space-y-6 pb-24 relative bg-[linear-gradient(to_right,rgba(43,29,48,0.015)_1px,transparent_1px),linear-gradient(to_bottom,rgba(43,29,48,0.015)_1px,transparent_1px)] bg-[size:24px_24px]">
      
      {/* Toast Notification Container */}
      {toast && (
        <div className={`fixed top-5 right-5 z-50 p-4 border shadow-lg max-w-md transition-all rounded-none ${
          toast.type === "success" ? "bg-[#2B1D30] text-white border-primary-500" :
          toast.type === "warning" ? "bg-amber-900 text-white border-amber-500" :
          "bg-rose-900 text-white border-rose-500"
        }`}>
          <div className="flex items-center justify-between gap-4">
            <span className="text-xs font-semibold">{toast.message}</span>
            <button onClick={() => setToast(null)} className="text-white/60 hover:text-white text-xs font-bold">X</button>
          </div>
        </div>
      )}

      {/* Patient header info */}
      <div className="border border-card-border bg-card-bg p-4 shadow-[0_1px_3px_rgba(110,37,130,0.06)] flex flex-wrap justify-between items-center rounded-lg">
        <div>
          <span className="text-[10px] font-bold text-primary-600 uppercase tracking-widest">Phiên khám hiện hành</span>
          <h1 className="text-xl font-bold text-[#2B1D30]">{patientName}</h1>
          <p className="text-xs text-[#6A5C70]">{patientAge} tuổi · {patientGender}</p>
        </div>
        <div className="flex gap-4">
          <div className="text-right">
            <span className="text-[10px] uppercase font-bold text-gray-400">Trạng thái khám</span>
            <div className="mt-1">
              <span className={`px-2 py-0.5 text-xs font-semibold rounded-md ${
                consultation?.status === "IN_PROGRESS" 
                  ? "bg-[#E8F1FD] text-[#2F80ED] border border-[#2F80ED]/20" 
                  : "bg-success-light text-success border border-success/20"
              }`}>
                {consultation?.status === "IN_PROGRESS" ? "Đang khám" : "Hoàn tất"}
              </span>
            </div>
          </div>
          {prescriptionStatus && (
            <div className="text-right">
              <span className="text-[10px] uppercase font-bold text-gray-400">Trạng thái đơn</span>
              <div className="mt-1">
                <span className={`px-2 py-0.5 text-xs font-semibold rounded-md ${
                  prescriptionStatus === "DRAFT"
                    ? "bg-warning-light text-warning border border-warning/20"
                    : "bg-success-light text-success border border-success/20"
                }`}>
                  {prescriptionStatus === "DRAFT" ? "Đơn nháp" : "Đã ký xác nhận"}
                </span>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Structured 3-Level Allergy Banners */}
      {allergyLoaded && (
        patientAllergies.length > 0 ? (
          <div className="space-y-2">
            {/* Level 1 — CRITICAL (Clean Clinical Red Notice) */}
            {patientAllergies.filter(a => a.severity === "CRITICAL").length > 0 && (
              <div className="border border-red-200 border-l-4 border-l-red-600 bg-red-50/90 p-3.5 flex items-center justify-between rounded-md shadow-xs">
                <div className="flex items-center gap-3">
                  <svg className="w-4 h-4 text-red-600 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-[10px] font-bold text-red-700 bg-red-100 border border-red-300 px-1.5 py-0.5 uppercase tracking-wider rounded-sm">
                        Mức 1 — Nguy hiểm cao
                      </span>
                      <span className="text-xs font-bold text-red-900">
                        Cảnh báo Dị ứng Thuốc & Nguy cơ Phản vệ
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-2 mt-1">
                      {patientAllergies.filter(a => a.severity === "CRITICAL").map((a, idx) => (
                        <span key={idx} className="text-xs font-bold text-red-800">
                          {a.allergyName} <span className="font-normal text-red-700">({a.reaction || 'Phản ứng nặng'})</span>
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
                <span className="text-[10px] bg-red-100 text-red-800 px-2 py-1 uppercase font-bold tracking-wider border border-red-300 rounded-sm">
                  Tự động chặn kê đơn trùng nhóm
                </span>
              </div>
            )}

            {/* Level 2 — WARNING (Clean Clinical Amber Notice) */}
            {patientAllergies.filter(a => a.severity === "WARNING").length > 0 && (
              <div className="border border-amber-200 border-l-4 border-l-amber-500 bg-amber-50/90 p-3 flex items-center gap-3 rounded-md shadow-xs text-amber-900">
                <svg className="w-4 h-4 text-amber-600 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] font-bold text-amber-800 bg-amber-100 border border-amber-300 px-1.5 py-0.5 uppercase tracking-wider">
                      Mức 2 — Cần lưu ý
                    </span>
                    <span className="text-xs font-semibold text-amber-900">
                      {patientAllergies.filter(a => a.severity === "WARNING").map(a => `${a.allergyName} (${a.reaction || 'Lưu ý'})`).join(' • ')}
                    </span>
                  </div>
                </div>
              </div>
            )}
          </div>
        ) : allergyNotes ? (
          <div className="border border-rose-200 bg-rose-50 p-3.5 flex items-center gap-3 rounded-none shadow-xs">
            <svg className="w-4 h-4 text-rose-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <div>
              <span className="text-[11px] font-bold text-rose-800 uppercase tracking-wider block">Cảnh báo Dị ứng Thuốc (Tóm tắt)</span>
              <span className="text-xs font-semibold text-rose-700">{allergyNotes}</span>
            </div>
          </div>
        ) : (
          <div className="border border-slate-200 bg-slate-50 px-3.5 py-2 flex items-center gap-2 rounded-none">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block flex-shrink-0" />
            <span className="text-[11px] text-slate-500 font-medium">Không ghi nhận tiền sử dị ứng thuốc</span>
          </div>
        )
      )}

      {/* Locked Consultation Banner */}
      {isConsultationLocked && (
        <div className="border border-amber-300 bg-amber-50 p-4 text-amber-900 text-xs font-semibold flex items-center justify-between rounded-lg shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-bold text-amber-700 uppercase tracking-wider text-[11px]">Đã khóa phiên khám</span>
            <span>· Phiên khám này đã HOÀN TẤT. Hồ sơ bệnh án đã được lưu trữ an toàn và ở chế độ chỉ xem (Read-Only).</span>
          </div>
          <button 
            type="button"
            onClick={() => router.push("/dashboard")}
            className="px-3 py-1 bg-amber-800 text-white text-xs font-bold hover:bg-amber-900 rounded-md transition-all cursor-pointer"
          >
            Về Bảng điều khiển
          </button>
        </div>
      )}

      {/* Awaiting CLS Banner */}
      {consultation?.status === "AWAITING_CLS" && !isConsultationLocked && (
        <div className="border border-blue-300 bg-blue-50 p-4 text-blue-900 text-xs font-semibold flex items-center justify-between rounded-lg shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-bold text-blue-700 uppercase tracking-wider text-[11px]">Chờ Cận lâm sàng</span>
            <span>· Đã tạo chỉ định Cận lâm sàng. Đang chờ bệnh nhân tới phòng kỹ thuật và chờ trả kết quả.</span>
          </div>
        </div>
      )}

      {/* Awaiting Review Banner */}
      {consultation?.status === "AWAITING_REVIEW" && !isConsultationLocked && (
        <div className="border border-purple-300 bg-purple-50 p-4 text-purple-900 text-xs font-semibold flex items-center justify-between rounded-lg shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-bold text-purple-700 uppercase tracking-wider text-[11px]">Đã có kết quả CLS</span>
            <span>· Kết quả Cận lâm sàng đã sẵn sàng. Bác sĩ bấm "Tiếp tục đọc kết quả" để xem và hoàn tất ca khám.</span>
          </div>
          <button
            type="button"
            onClick={async () => {
              try {
                await consultationApi.updateStatus(consultationId, "IN_PROGRESS");
                setConsultation(prev => prev ? { ...prev, status: "IN_PROGRESS" } : null);
                showToast("Đã chuyển sang đọc kết quả và tiếp tục phiên khám.", "success");
              } catch {
                showToast("Không thể cập nhật trạng thái phiên khám.", "danger");
              }
            }}
            className="px-3.5 py-1.5 bg-[#6E2582] text-white text-xs font-bold hover:bg-[#561A66] rounded-md transition-all cursor-pointer shadow-xs"
          >
            Tiếp tục đọc kết quả
          </button>
        </div>
      )}

      {/* Main two-column workspace */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        
        {/* Left Column (3/5): Tabbed Clinical & Diagnostic Data */}
        <div className="lg:col-span-3 space-y-4">
          
          {/* Navigation Tabs */}
          <div className="border-b border-card-border flex bg-card-bg rounded-t-lg">
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
              2. Triệu chứng & Lâm sàng
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
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all rounded-tr-lg ${
                activeTab === "summary"
                  ? "border-primary-600 text-primary-600 bg-[#F8F6F9]"
                  : "border-transparent text-[#6A5C70] hover:text-[#2B1D30]"
              }`}
            >
              5. Tóm tắt lâm sàng
            </button>
          </div>

          {/* TAB 1: Vital Signs with Real-time threshold validation */}
          {activeTab === "vitals" && (
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-4">
              <div className="flex justify-between items-center border-b border-card-border pb-2">
                <h2 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide">
                  Chỉ số sinh hiệu bệnh nhân
                </h2>
                <span className="text-[10px] text-[#6A5C70]">Tự động phát hiện bất thường</span>
              </div>
              
              <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Nhiệt độ (°C)</label>
                  <input
                    type="number"
                    step="0.1"
                    disabled={isConsultationLocked}
                    value={temperature}
                    onChange={(e) => setTemperature(e.target.value)}
                    placeholder="36.5"
                    className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
                      tempWarn
                        ? tempWarn.level === "danger"
                          ? "border-rose-500 bg-rose-50 font-bold"
                          : "border-amber-500 bg-amber-50 font-bold"
                        : "border-input-border bg-input-bg focus:border-input-focus"
                    }`}
                  />
                  {tempWarn && (
                    <span className={`text-[10px] font-bold mt-1 block ${
                      tempWarn.level === "danger" ? "text-rose-600" : "text-amber-600"
                    }`}>
                      Cảnh báo: {tempWarn.text}
                    </span>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Huyết áp (mmHg)</label>
                  <input
                    type="text"
                    disabled={isConsultationLocked}
                    value={bloodPressure}
                    onChange={(e) => setBloodPressure(e.target.value)}
                    placeholder="120/80"
                    className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
                      bpWarn
                        ? bpWarn.level === "danger"
                          ? "border-rose-500 bg-rose-50 font-bold"
                          : "border-amber-500 bg-amber-50 font-bold"
                        : "border-input-border bg-input-bg focus:border-input-focus"
                    }`}
                  />
                  {bpWarn && (
                    <span className={`text-[10px] font-bold mt-1 block ${
                      bpWarn.level === "danger" ? "text-rose-600" : "text-amber-600"
                    }`}>
                      Cảnh báo: {bpWarn.text}
                    </span>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Mạch (lần/phút)</label>
                  <input
                    type="number"
                    disabled={isConsultationLocked}
                    value={heartRate}
                    onChange={(e) => setHeartRate(e.target.value)}
                    placeholder="80"
                    className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
                      hrWarn ? "border-amber-500 bg-amber-50 font-bold" : "border-input-border bg-input-bg focus:border-input-focus"
                    }`}
                  />
                  {hrWarn && (
                    <span className="text-[10px] font-bold mt-1 block text-amber-600">
                      Cảnh báo: {hrWarn.text}
                    </span>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">SpO2 (%)</label>
                  <input
                    type="number"
                    disabled={isConsultationLocked}
                    value={spo2}
                    onChange={(e) => setSpo2(e.target.value)}
                    placeholder="98"
                    className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
                      spo2Warn ? "border-rose-500 bg-rose-50 font-bold" : "border-input-border bg-input-bg focus:border-input-focus"
                    }`}
                  />
                  {spo2Warn && (
                    <span className="text-[10px] font-bold mt-1 block text-rose-600">
                      Cảnh báo: {spo2Warn.text}
                    </span>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Chiều cao (cm)</label>
                  <input
                    type="number"
                    step="0.1"
                    disabled={isConsultationLocked}
                    value={height}
                    onChange={(e) => setHeight(e.target.value)}
                    placeholder="165"
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Cân nặng (kg)</label>
                  <input
                    type="number"
                    step="0.1"
                    disabled={isConsultationLocked}
                    value={weight}
                    onChange={(e) => setWeight(e.target.value)}
                    placeholder="60"
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
                  />
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: Clinical Details */}
          {activeTab === "clinical" && (
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-4">
              <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
                Ghi nhận bệnh sử & Thăm khám
              </h2>
              <div>
                <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Triệu chứng & Diễn biến bệnh</label>
                <textarea
                  disabled={isConsultationLocked}
                  value={symptoms}
                  onChange={(e) => setSymptoms(e.target.value)}
                  rows={4}
                  placeholder="Ghi nhận triệu chứng cơ năng, thời gian khởi phát..."
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Nhận định & Tóm tắt khám lâm sàng</label>
                <textarea
                  disabled={isConsultationLocked}
                  value={clinicalNotes}
                  onChange={(e) => setClinicalNotes(e.target.value)}
                  rows={4}
                  placeholder="Nhận định của bác sĩ qua thăm khám thể chất..."
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                />
              </div>
            </div>
          )}

          {/* TAB 3: Diagnosis & ICD-10 */}
          {activeTab === "diagnosis" && (
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-4">
              <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
                Chẩn đoán bệnh theo chuẩn y tế
              </h2>
              
              {!isConsultationLocked && (
                <div className="relative">
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tra cứu Mã ICD-10</label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={icdSearchQuery}
                      onChange={(e) => {
                        setIcdSearchQuery(e.target.value);
                        setShowIcdDropdown(true);
                      }}
                      onFocus={() => setShowIcdDropdown(true)}
                      placeholder="Nhập mã hoặc tên bệnh (ví dụ: K21, Tăng huyết áp...)"
                      className="flex-1 border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                    />
                    {icdSearchQuery && (
                      <button 
                        onClick={() => { setIcdSearchQuery(""); setShowIcdDropdown(false); }}
                        className="border border-card-border px-3 text-xs hover:bg-[#F8F6F9] rounded-md"
                      >
                        Xóa
                      </button>
                    )}
                  </div>

                  {showIcdDropdown && filteredIcd10.length > 0 && (
                    <div className="absolute left-0 right-0 z-20 mt-1 border border-card-border bg-card-bg shadow-md max-h-48 overflow-y-auto rounded-md">
                      {filteredIcd10.map((item) => (
                        <div
                          key={item.code}
                          onClick={() => selectIcd10(item.code, item.name)}
                          className="px-4 py-2 text-sm hover:bg-[#F3E8F5] cursor-pointer text-[#2B1D30] flex justify-between"
                        >
                          <span className="font-mono font-bold text-primary-600">{item.code}</span>
                          <span className="text-[#6A5C70] truncate max-w-xs">{item.name}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              <div className="grid grid-cols-3 gap-4">
                <div className="col-span-1">
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Mã ICD-10 đã chọn</label>
                  <input
                    type="text"
                    readOnly
                    value={icd10Code}
                    placeholder="Chưa chọn"
                    className="w-full border border-card-border bg-gray-50 px-3 py-2 text-sm text-[#2B1D30] font-mono font-bold focus:outline-none rounded-md"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tên bệnh theo ICD</label>
                  <input
                    type="text"
                    readOnly
                    value={icd10Name}
                    placeholder="Chưa chọn"
                    className="w-full border border-card-border bg-gray-50 px-3 py-2 text-sm text-[#6A5C70] focus:outline-none rounded-md"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Chẩn đoán chi tiết của bác sĩ</label>
                <textarea
                  disabled={isConsultationLocked}
                  value={diagnosis}
                  onChange={(e) => setDiagnosis(e.target.value)}
                    rows={3}
                  placeholder="Nhập chẩn đoán lâm sàng chi tiết..."
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                />
              </div>
            </div>
          )}

          {/* TAB 4: Lab Orders (Chỉ định Cận lâm sàng) */}
          {activeTab === "labs" && (
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-5">
              <div className="flex items-center justify-between border-b border-card-border pb-3">
                <div>
                  <h2 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide">
                    Chỉ định Cận lâm sàng (Xét nghiệm & Cẩn lâm sàng)
                  </h2>
                  <p className="text-[11px] text-[#6A5C70] mt-0.5">
                    Chọn các chỉ định cận lâm sàng cần thiết cho ca khám. Hệ thống sẽ tự động tạo lượt xếp hàng tại khu tương ứng.
                  </p>
                </div>
                {selectedLabServices.length > 0 && !isConsultationLocked && (
                  <button
                    type="button"
                    disabled={isSubmittingLabOrder}
                    onClick={async () => {
                      if (!user || !consultation) return;
                      setIsSubmittingLabOrder(true);
                      try {
                        const items = selectedLabServices.map(s => ({
                          serviceCode: s.serviceCode,
                          serviceName: s.serviceName,
                          servicePointId: s.servicePointId,
                          required: true,
                          preparationInstruction: s.preparationInstruction
                        }));
                        const res = await labApi.createOrder({
                          consultationId,
                          patientId: consultation.patientId,
                          items,
                          clinicalNote: labClinicalNote || symptoms || "Chỉ định cận lâm sàng",
                          paymentRequired: true
                        });
                        try {
                          await consultationApi.updateStatus(consultationId, "AWAITING_CLS");
                          setConsultation(prev => prev ? { ...prev, status: "AWAITING_CLS" } : null);
                        } catch {
                          /* Ignore status update failure */
                        }
                        showToast("Đã tạo chỉ định Cận lâm sàng thành công. Bệnh nhân đã được tự động xếp lượt!", "success");
                        setSelectedLabServices([]);
                        setLabClinicalNote("");
                        const updatedOrders = await labApi.getByConsultation(consultationId);
                        setExistingLabOrders(updatedOrders.data);
                      } catch (err) {
                        showToast(err instanceof Error ? err.message : "Không thể tạo chỉ định Cận lâm sàng.", "danger");
                      } finally {
                        setIsSubmittingLabOrder(false);
                      }
                    }}
                    className="px-3.5 py-1.5 bg-[#3B82F6] hover:bg-[#2563EB] text-white text-xs font-semibold rounded-md shadow-xs transition-all flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                  >
                    {isSubmittingLabOrder ? "Đang gửi chỉ định..." : `Gửi ${selectedLabServices.length} chỉ định CLS`}
                  </button>
                )}
              </div>

              {/* Danh sách Chỉ định đã được tạo */}
              {existingLabOrders.length > 0 && (
                <div className="space-y-3">
                  <h3 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wider flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-indigo-500 inline-block" />
                    <span>Các Chỉ định CLS đã phát hành ({existingLabOrders.length})</span>
                  </h3>
                  <div className="space-y-2">
                    {existingLabOrders.map((order) => (
                      <div key={order.id} className="border border-indigo-100 bg-indigo-50/50 p-3 rounded-md space-y-2">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-mono font-bold text-indigo-900">Order ID: {order.id.slice(0, 8)}...</span>
                          <span className={`px-2 py-0.5 text-[10px] font-bold uppercase rounded ${
                            order.status === "RESULT_AVAILABLE" ? "bg-emerald-100 text-emerald-800" :
                            order.status === "IN_PROGRESS" ? "bg-purple-100 text-purple-800" :
                            "bg-amber-100 text-amber-800"
                          }`}>
                            {order.status === "RESULT_AVAILABLE" ? "Đã có kết quả" :
                             order.status === "IN_PROGRESS" ? "Đang thực hiện" : "Đã vào hàng chờ CLS"}
                          </span>
                        </div>
                        <div className="divide-y divide-indigo-100 border border-indigo-100 bg-white rounded-md text-xs">
                          {order.items.map(item => (
                            <div key={item.id} className="p-2.5 flex items-center justify-between">
                              <div>
                                <span className="font-semibold text-gray-900">{item.serviceName}</span>
                                <span className="text-[11px] text-gray-500 block">Điểm thực hiện: {item.servicePointId}</span>
                              </div>
                              {item.resultValue ? (
                                <div className="text-right font-mono text-xs">
                                  <span className="font-bold text-emerald-700">{item.resultValue} {item.unit || ""}</span>
                                  {item.referenceRange && <span className="text-[10px] text-gray-400 block">Chuẩn: {item.referenceRange}</span>}
                                </div>
                              ) : (
                                <span className="text-[11px] text-amber-600 italic">Chờ KTV nhập kết quả</span>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Danh mục các Xét nghiệm / Dịch vụ để Bác sĩ chọn */}
              {!isConsultationLocked && (
                <div className="space-y-3">
                  <h3 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wider">
                    Chọn dịch vụ Cận lâm sàng từ Danh mục
                  </h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {MOCK_LAB_SERVICES.map((service) => {
                      const isSelected = selectedLabServices.some(s => s.serviceCode === service.serviceCode);
                      return (
                        <div
                          key={service.serviceCode}
                          onClick={() => {
                            if (isSelected) {
                              setSelectedLabServices(prev => prev.filter(s => s.serviceCode !== service.serviceCode));
                            } else {
                              setSelectedLabServices(prev => [...prev, service]);
                            }
                          }}
                          className={`p-3 border rounded-lg cursor-pointer transition-all ${
                            isSelected
                              ? "border-primary-600 bg-primary-50/40 shadow-xs"
                              : "border-card-border bg-white hover:border-gray-300"
                          }`}
                        >
                          <div className="flex items-start justify-between">
                            <div className="space-y-1">
                              <span className="text-xs font-bold text-gray-900">{service.serviceName}</span>
                              <span className="text-[11px] text-gray-500 block">{service.servicePointName}</span>
                              {service.preparationInstruction && (
                                <span className="text-[10px] text-amber-700 bg-amber-50 border border-amber-200 px-1.5 py-0.5 rounded inline-block">
                                  {service.preparationInstruction}
                                </span>
                              )}
                            </div>
                            <input
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => {}}
                              className="h-4 w-4 text-primary-600 rounded focus:ring-primary-500 cursor-pointer mt-0.5"
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Ghi chú chỉ định cận lâm sàng cho KTV</label>
                    <input
                      type="text"
                      value={labClinicalNote}
                      onChange={(e) => setLabClinicalNote(e.target.value)}
                      placeholder="Ví dụ: Kiểm tra nghi ngờ viêm dạ dày, loại trừ thiếu máu..."
                      className="w-full border border-input-border bg-input-bg px-3 py-2 text-xs text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB 5: Clinical Summary */}
          {activeTab === "summary" && (
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-5">
              <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
                Tóm tắt Lâm sàng & Lịch sử Y tế Bệnh nhân
              </h2>

              {summaryLoading ? (
                <div className="space-y-4 py-2">
                  <div className="h-16 bg-gray-100 animate-pulse rounded-md" />
                  <div className="h-28 bg-gray-100 animate-pulse rounded-md" />
                  <div className="h-24 bg-gray-100 animate-pulse rounded-md" />
                </div>
              ) : (
                <div className="space-y-5">
                  {/* Section A: EMR Master Record Card (Hồ sơ Bệnh án Điện tử) */}
                  <div className="border border-purple-200 bg-purple-50/40 p-4 space-y-3 rounded-md">
                    <div className="flex items-center justify-between border-b border-purple-200/60 pb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] font-bold text-purple-700 uppercase tracking-widest bg-purple-100 px-2 py-0.5 border border-purple-200 rounded-sm">
                          Hồ sơ EMR Master
                        </span>
                        <span className="text-xs font-mono font-bold text-purple-900">
                          {emrRecord?.recordNumber || "EMR-2026-PENDING"}
                        </span>
                      </div>
                      {emrRecord?.bloodType && (
                        <span className="text-xs font-bold text-rose-700 bg-rose-50 px-2 py-0.5 border border-rose-200 flex items-center gap-1 rounded-sm">
                          <span>🩸 Nhóm máu:</span>
                          <span>{emrRecord.bloodType}</span>
                        </span>
                      )}
                    </div>

                    <div className="space-y-1">
                      <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                        Bệnh nền & Tiền sử y tế
                      </span>
                      <p className="text-xs font-medium text-slate-800">
                        {emrRecord?.medicalHistory || patientMedicalHistory || "Chưa ghi nhận thông tin bệnh nền mạn tính"}
                      </p>
                    </div>
                  </div>

                  {/* Section B: Consultation History (Past visits) */}
                  <div className="space-y-3">
                    <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                      <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                        Lịch sử khám bệnh gần đây
                      </span>
                      <span className="text-[10px] text-slate-400 font-mono">
                        {(consultationHistory ?? []).length} ca khám trước (Cuộn để xem thêm)
                      </span>
                    </div>

                    {(consultationHistory ?? []).length === 0 ? (
                      <div className="py-6 text-center text-xs text-slate-400 italic bg-slate-50 border border-dashed border-slate-200 rounded-md">
                        Đây là lần khám đầu tiên của bệnh nhân trong hệ thống CareFlow
                      </div>
                    ) : (
                      <div className="divide-y divide-slate-100 border border-slate-200 rounded-md max-h-[260px] overflow-y-auto pr-1">
                        {(consultationHistory ?? []).map((c) => {
                          const linkedPrescription = (prescriptionHistory ?? []).find(p => p.consultationId === c.id);
                          return (
                            <div 
                              key={c.id} 
                              onClick={() => setSelectedHistoryConsultation(c)}
                              className="p-3 hover:bg-purple-50/50 transition-colors space-y-1.5 cursor-pointer group"
                            >
                              <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block" />
                                  <span className="text-xs font-bold text-slate-800 group-hover:text-primary-700">
                                    {c.icd10Name ? `${c.icd10Code ? `${c.icd10Code} - ` : ""}${c.icd10Name}` : c.diagnosis || "Chẩn đoán chưa cập nhật"}
                                  </span>
                                </div>
                                <div className="flex items-center gap-2">
                                  <span className="text-[11px] font-mono text-slate-400">
                                    {c.startedAt ? new Date(c.startedAt).toLocaleDateString("vi-VN") : "N/A"}
                                  </span>
                                  <span className="text-[10px] text-primary-600 font-semibold opacity-0 group-hover:opacity-100 transition-opacity">
                                    Xem chi tiết →
                                  </span>
                                </div>
                              </div>

                              {/* Vitals summary line */}
                              <div className="text-[11px] text-slate-500 flex flex-wrap gap-x-3 gap-y-0.5 items-center justify-between">
                                <div className="flex flex-wrap gap-x-3 gap-y-0.5">
                                  {c.bloodPressure && <span>Huyết áp: <strong className="font-mono text-slate-700">{c.bloodPressure}</strong></span>}
                                  {c.temperature && <span>Thân nhiệt: <strong className="font-mono text-slate-700">{c.temperature}°C</strong></span>}
                                  {c.spo2 && <span>SpO2: <strong className="font-mono text-slate-700">{c.spo2}%</strong></span>}
                                  {c.heartRate && <span>Mạch: <strong className="font-mono text-slate-700">{c.heartRate} bpm</strong></span>}
                                </div>
                                {linkedPrescription ? (
                                  <span className="text-[10px] font-semibold text-purple-700 bg-purple-50 px-1.5 py-0.5 border border-purple-200 rounded-sm">
                                    Đã kê đơn thuốc
                                  </span>
                                ) : (
                                  <span className="text-[10px] text-slate-400 italic">
                                    Chưa kê đơn
                                  </span>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>

                  {/* Section C: Prescription History (Past prescriptions) */}
                  <div className="space-y-3">
                    <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                      <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                        Đơn thuốc đã dùng gần đây
                      </span>
                      <span className="text-[10px] text-slate-400 font-mono">
                        {(prescriptionHistory ?? []).length} đơn (Cuộn để xem thêm)
                      </span>
                    </div>

                    {(prescriptionHistory ?? []).length === 0 ? (
                      <div className="py-6 text-center text-xs text-slate-400 italic bg-slate-50 border border-dashed border-slate-200 rounded-md">
                        Chưa ghi nhận lịch sử đơn thuốc cũ
                      </div>
                    ) : (
                      <div className="space-y-2.5 max-h-[260px] overflow-y-auto pr-1">
                        {(prescriptionHistory ?? []).map((p) => {
                          const linkedConsultation = (consultationHistory ?? []).find(c => c.id === p.consultationId);
                          return (
                            <div 
                              key={p.id} 
                              onClick={() => setSelectedHistoryPrescription(p)}
                              className="border border-slate-200 p-3 bg-white space-y-2 rounded-md hover:border-primary-400 transition-colors cursor-pointer group"
                            >
                              <div className="flex items-center justify-between text-[11px] border-b border-slate-100 pb-1.5">
                                <div className="flex items-center gap-2">
                                  <span className="font-bold text-slate-800 group-hover:text-primary-700">
                                    Đơn thuốc · {new Date(p.createdAt).toLocaleDateString("vi-VN")}
                                  </span>
                                  {linkedConsultation && (
                                    <span className="text-[10px] font-mono text-slate-500 truncate max-w-[200px]">
                                      ({linkedConsultation.icd10Code || linkedConsultation.diagnosis || "Lần khám"})
                                    </span>
                                  )}
                                </div>
                                <div className="flex items-center gap-2">
                                  <span className={`px-1.5 py-0.5 font-bold uppercase text-[9px] rounded-sm ${
                                    p.status === "CONFIRMED" || p.status === "DISPENSED"
                                      ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                                      : "bg-amber-50 text-amber-700 border border-amber-200"
                                  }`}>
                                    {p.status === "CONFIRMED" ? "Đã ký" : p.status === "DISPENSED" ? "Đã cấp phát" : "Nháp"}
                                  </span>
                                  <span className="text-[10px] text-primary-600 font-semibold opacity-0 group-hover:opacity-100 transition-opacity">
                                    Chi tiết →
                                  </span>
                                </div>
                              </div>
                              <ul className="space-y-1">
                                {(p.items || []).slice(0, 3).map((item, idx) => (
                                  <li key={item.id || idx} className="text-xs text-slate-600 flex justify-between">
                                    <span>
                                      <strong className="text-slate-800">{idx + 1}. {item.medicineName}</strong>
                                      {item.dosage && <span className="text-slate-500 font-normal"> · {item.dosage}</span>}
                                      {item.frequency && <span className="text-slate-500 font-normal"> · {item.frequency}</span>}
                                    </span>
                                    <span className="font-mono text-slate-500 text-[11px]">x{item.quantity} {item.unit}</span>
                                  </li>
                                ))}
                                {(p.items || []).length > 3 && (
                                  <li className="text-[11px] text-primary-600 font-semibold italic">
                                    + {p.items.length - 3} loại thuốc khác... (Bấm để xem đầy đủ)
                                  </li>
                                )}
                              </ul>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}

        </div>

        {/* Right Column (2/5): Prescription List & Actions */}
        <div className="lg:col-span-2 space-y-6">
          
          {/* Main Prescription Container */}
          <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-lg flex flex-col justify-between min-h-[420px]">
            <div>
              <div className="flex justify-between items-center border-b border-card-border pb-3 mb-4">
                <div>
                  <h2 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide">
                    Danh sách Đơn thuốc
                  </h2>
                  <p className="text-[10px] text-[#6A5C70]">Tổng số thuốc: {prescriptionItems.length}</p>
                </div>
                <div className="flex items-center gap-3">
                  {prescriptionItems.length > 0 && !isPrescriptionLocked && (
                    <button
                      type="button"
                      onClick={clearAllMedicines}
                      className="text-[11px] font-medium text-slate-400 hover:text-rose-600 transition-colors hover:underline flex items-center gap-1 cursor-pointer"
                    >
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                      <span>Xóa toàn bộ đơn</span>
                    </button>
                  )}
                  {!isPrescriptionLocked && (
                    <button
                      type="button"
                      onClick={openAddMedicineDrawer}
                      className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-3 py-1.5 text-xs transition-all flex items-center gap-1.5 rounded-md shadow-sm"
                    >
                      + THÊM THUỐC
                    </button>
                  )}
                </div>
              </div>

              {/* Prescription Items List or Empty State */}
              {prescriptionItems.length === 0 ? (
                <div className="border-2 border-dashed border-card-border p-8 text-center bg-[#FAF8FA] space-y-3 rounded-lg my-4">
                  <div className="w-10 h-10 border border-primary-300 bg-white flex items-center justify-center mx-auto text-primary-600 font-bold text-lg rounded-md">
                    Rx
                  </div>
                  <div>
                    <p className="text-sm font-bold text-[#2B1D30]">Chưa có thuốc nào trong đơn</p>
                    <p className="text-xs text-[#6A5C70]">Nhấn nút bên dưới để mở công cụ kê đơn thuốc cho bệnh nhân</p>
                  </div>
                  {!isPrescriptionLocked && (
                    <button
                      type="button"
                      onClick={openAddMedicineDrawer}
                      className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-4 py-2 text-xs transition-all rounded-md inline-block mt-2"
                    >
                      + THÊM THUỐC ĐẦU TIÊN
                    </button>
                  )}
                </div>
              ) : (
                <div className="space-y-3 max-h-[320px] overflow-y-auto pr-1">
                  {prescriptionItems.map((item, index) => (
                    <div key={item.medicineCode} className="border border-card-border p-3 bg-white flex justify-between items-start hover:border-primary-400 transition-all rounded-md">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold text-primary-600">{index + 1}.</span>
                          <span className="font-bold text-sm text-[#2B1D30]">{item.medicineName}</span>
                        </div>
                        <p className="text-xs text-[#6A5C70]">
                          Mã: <span className="font-mono">{item.medicineCode}</span> | Số lượng: <strong className="text-[#2B1D30]">{item.quantity} {item.unit}</strong> ({item.duration} ngày)
                        </p>
                        <p className="text-xs text-[#6A5C70] font-medium">
                          Liều: {item.dosage} · {item.frequency} · {item.timing}
                        </p>
                        {item.notes && <p className="text-xs text-amber-700 bg-amber-50 p-1 border border-amber-200 mt-1 rounded-sm">Ghi chú: {item.notes}</p>}
                      </div>

                      {!isPrescriptionLocked && (
                        <div className="flex items-center gap-3">
                          <button
                            type="button"
                            onClick={() => openEditMedicineDrawer(item)}
                            className="text-[11px] font-medium text-slate-500 hover:text-primary-700 flex items-center gap-1 transition-colors cursor-pointer"
                            title="Sửa thông tin thuốc"
                          >
                            <svg className="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                            </svg>
                            <span>Sửa</span>
                          </button>
                          <button
                            type="button"
                            onClick={() => removeMedicine(item.medicineCode, item.medicineName)}
                            className="text-[11px] font-medium text-slate-400 hover:text-rose-600 flex items-center gap-1 transition-colors cursor-pointer"
                            title="Xóa khỏi đơn"
                          >
                            <svg className="w-3.5 h-3.5 text-slate-400 hover:text-rose-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                            <span>Xóa</span>
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Progressive Disclosure: General Notes for Prescription */}
            <div className="pt-4 border-t border-card-border mt-4">
              {!showPrescriptionNotes && !prescriptionNotes ? (
                <button
                  type="button"
                  onClick={() => setShowPrescriptionNotes(true)}
                  className="text-xs font-bold text-primary-600 hover:underline flex items-center gap-1"
                >
                  + Thêm ghi chú đơn thuốc chung
                </button>
              ) : (
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="block text-xs font-semibold text-[#6A5C70]">Ghi chú đơn thuốc chung</label>
                    {!isPrescriptionLocked && (
                      <button 
                        onClick={() => { setShowPrescriptionNotes(false); setPrescriptionNotes(""); }}
                        className="text-[10px] text-gray-400 hover:text-rose-600"
                      >
                        Thu gọn
                      </button>
                    )}
                  </div>
                  <textarea
                    disabled={isPrescriptionLocked}
                    value={prescriptionNotes}
                    onChange={(e) => setPrescriptionNotes(e.target.value)}
                    rows={2}
                    placeholder="Ghi chú về chế độ ăn kiêng, dị ứng hoặc lời dặn của bác sĩ..."
                    className="w-full border border-input-border bg-input-bg px-3 py-1.5 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                  />
                </div>
              )}
            </div>
          </div>

          {/* Follow-up Date Panel */}
          <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] space-y-3 rounded-lg">
            <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
              Lịch Hẹn Tái khám
            </h2>
            <div>
              <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Ngày hẹn tái khám</label>
              <input
                type="date"
                disabled={isPrescriptionLocked}
                value={followUpDate}
                onChange={(e) => setFollowUpDate(e.target.value)}
                className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
              />
            </div>
            <p className="text-[10px] text-[#6A5C70]">
              * Bệnh nhân sẽ nhận được thông báo tái khám tự động trên ứng dụng di động dựa theo ngày đã chọn.
            </p>
          </div>

        </div>

      </div>

      {/* SLIDE-OVER DRAWER MODAL FOR MEDICINE ENTRY */}
      {isMedicineDrawerOpen && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-xs">
          <div className="w-full max-w-lg bg-card-bg border-l border-card-border shadow-2xl h-full flex flex-col justify-between p-6 animate-in slide-in-from-right duration-200 rounded-l-xl">
            
            <div className="flex-1 overflow-y-auto pr-1 space-y-5">
              <div className="flex justify-between items-center border-b border-card-border pb-3">
                <h3 className="text-base font-bold text-[#2B1D30] uppercase tracking-wide">
                  {editingMedicineCode ? "Cập nhật thông tin thuốc" : "Thêm thuốc vào đơn kê"}
                </h3>
                <button
                  type="button"
                  onClick={() => setIsMedicineDrawerOpen(false)}
                  className="text-gray-400 hover:text-black font-bold text-sm px-2"
                >
                  X
                </button>
              </div>

              {/* Search-as-you-type Autocomplete for Medicine Selection */}
              <div className="relative z-40">
                <label className="block text-xs font-bold text-[#2B1D30] mb-1">Tìm kiếm & Chọn thuốc (*)</label>
                <input
                  type="text"
                  value={medSearchQuery}
                  onChange={(e) => {
                    setMedSearchQuery(e.target.value);
                    setShowMedDropdown(true);
                  }}
                  onFocus={() => setShowMedDropdown(true)}
                  placeholder="Nhập tên thuốc hoặc mã thuốc..."
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                />

                {showMedDropdown && filteredMedicines.length > 0 && (
                  <div className="absolute left-0 right-0 top-full z-50 mt-1 border border-card-border bg-white shadow-2xl max-h-60 overflow-y-auto rounded-md divide-y divide-gray-100">
                    {filteredMedicines.map((m) => (
                      <div
                        key={m.code}
                        onClick={() => {
                          setSelectedMedicine(m);
                          setMedSearchQuery(m.name);
                          setShowMedDropdown(false);
                        }}
                        className="px-4 py-2.5 hover:bg-[#F3E8F5] cursor-pointer text-sm flex justify-between items-center transition-colors"
                      >
                        <div>
                          <p className="font-bold text-[#2B1D30]">{m.name}</p>
                          <p className="text-xs text-[#6A5C70]">Mã: <span className="font-mono">{m.code}</span> | Đơn vị: {m.unit}</p>
                        </div>
                        <span className="text-[10px] bg-primary-100 text-primary-700 px-2 py-0.5 font-semibold rounded-sm">
                          Khả dụng
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Selected Medicine Info Card */}
              {selectedMedicine && (
                <div className="border border-primary-200 bg-[#F8F6F9] p-3 text-xs space-y-1 rounded-md">
                  <p className="font-bold text-primary-700">{selectedMedicine.name}</p>
                  <p className="text-[#6A5C70]">Mã thuốc chuẩn: <span className="font-mono font-bold">{selectedMedicine.code}</span> | Đơn vị tính: <strong>{selectedMedicine.unit}</strong></p>
                </div>
              )}

              {/* Dosage & Frequency */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Liều dùng (*)</label>
                  <input
                    type="text"
                    value={medDosage}
                    onChange={(e) => setMedDosage(e.target.value)}
                    placeholder="1 viên / lần"
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tần suất (*)</label>
                  <input
                    type="text"
                    value={medFrequency}
                    onChange={(e) => setMedFrequency(e.target.value)}
                    placeholder="2 lần / ngày"
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
                  />
                </div>
              </div>

              {/* Timing, Duration & Quantity */}
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Thời điểm</label>
                  <input
                    type="text"
                    value={medTiming}
                    onChange={(e) => setMedTiming(e.target.value)}
                    placeholder="Sau khi ăn"
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Số ngày</label>
                  <input
                    type="number"
                    value={medDuration}
                    onChange={(e) => setMedDuration(e.target.value)}
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Số lượng (*)</label>
                  <input
                    type="number"
                    value={medQuantity}
                    onChange={(e) => setMedQuantity(e.target.value)}
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
                  />
                </div>
              </div>

              {/* Progressive Disclosure: Medicine Note */}
              <div>
                {!showMedNotesInput && !medNotes ? (
                  <button
                    type="button"
                    onClick={() => setShowMedNotesInput(true)}
                    className="text-xs font-semibold text-primary-600 hover:underline"
                  >
                    + Thêm ghi chú/lưu ý dùng thuốc này
                  </button>
                ) : (
                  <div>
                    <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Ghi chú uống thuốc</label>
                    <input
                      type="text"
                      value={medNotes}
                      onChange={(e) => setMedNotes(e.target.value)}
                      placeholder="Ví dụ: Uống với nhiều nước, tránh uống cùng sữa..."
                      className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
                    />
                  </div>
                )}
              </div>

            </div>

            {/* Drawer Actions */}
            <div className="pt-4 border-t border-card-border flex gap-3">
              <button
                type="button"
                onClick={() => setIsMedicineDrawerOpen(false)}
                className="flex-1 border border-card-border hover:bg-gray-100 text-[#2B1D30] font-bold py-2.5 text-xs transition-all rounded-md"
              >
                HỦY BỎ
              </button>
              <button
                type="button"
                onClick={saveMedicineToPrescription}
                className="flex-1 bg-primary-600 hover:bg-primary-700 text-white font-bold py-2.5 text-xs transition-all rounded-md shadow-sm"
              >
                {editingMedicineCode ? "LƯU CẬP NHẬT" : "XÁC NHẬN THÊM"}
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Sticky Bottom Action Bar */}
      <div className="fixed bottom-0 left-64 right-0 bg-[#2B1D30] border-t border-white/10 h-[64px] px-6 flex justify-between items-center z-30 shadow-2xl">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 bg-emerald-400 rounded-full inline-block"></span>
          <span className="text-xs text-white/70 font-medium">Bác sĩ phụ trách: <strong className="text-white">{user?.fullName}</strong></span>
        </div>
        
        <div className="flex gap-3">
          {!isConsultationLocked && (
            <button
              onClick={saveDraft}
              disabled={isSaving}
              className="border border-white/30 hover:bg-white/10 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-md"
            >
              {isSaving ? "ĐANG LƯU..." : "LƯU NHÁP ĐƠN"}
            </button>
          )}
          
          {prescriptionId && prescriptionStatus === "DRAFT" && prescriptionItems.length > 0 && !isConsultationLocked && (
            <button
              onClick={confirmPrescription}
              disabled={isSaving}
              className="border border-white/30 hover:bg-white/10 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-md"
            >
              {isSaving ? "ĐANG KÝ..." : "KÝ & XÁC NHẬN ĐƠN"}
            </button>
          )}

          {!isConsultationLocked ? (
            <button
              onClick={completeConsultation}
              disabled={isSaving}
              className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-md shadow-sm"
            >
              {isSaving ? "ĐANG XỬ LÝ..." : "HOÀN TẤT PHIÊN KHÁM"}
            </button>
          ) : (
            <button
              onClick={() => router.replace("/dashboard")}
              className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-6 py-2 text-xs transition-all rounded-md"
            >
              QUAY LẠI TRANG CHỦ
            </button>
          )}
        </div>
      </div>

      {/* Custom Confirmation Modal */}
      <ConfirmModal
        isOpen={confirmModalConfig.isOpen}
        title={confirmModalConfig.title}
        message={confirmModalConfig.message}
        variant={confirmModalConfig.variant}
        onConfirm={confirmModalConfig.onConfirm}
        onCancel={() => setConfirmModalConfig(prev => ({ ...prev, isOpen: false }))}
      >
        {confirmModalConfig.showItemList && prescriptionItems.length > 0 && (
          <div className="mt-3 space-y-2">
            <div className="text-[11px] font-bold text-[#2B1D30] uppercase border-b border-card-border pb-1 flex justify-between items-center">
              <span>Danh sách thuốc trong đơn ({prescriptionItems.length} loại):</span>
              {prescriptionItems.length > 5 && (
                <span className="text-[10px] text-primary-600 font-normal italic">Cuộn xuống để xem hết</span>
              )}
            </div>
            <div className="max-h-[160px] overflow-y-auto space-y-1.5 pr-1 custom-scrollbar">
              {prescriptionItems.map((item, idx) => (
                <div key={item.medicineCode} className="text-xs text-[#6A5C70] font-medium leading-relaxed">
                  <span className="font-bold text-[#2B1D30]">{idx + 1}. {item.medicineName}</span>{" "}
                  <span className="text-gray-900 font-semibold">x{item.quantity} {item.unit}</span>{" "}
                  <span className="text-gray-500">({item.frequency})</span>{" "}
                  <span className="text-gray-400 font-mono text-[11px]">- {item.medicineCode}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </ConfirmModal>

      {/* DETAIL MODAL: Selected History Consultation */}
      {selectedHistoryConsultation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white border border-card-border shadow-2xl rounded-xl w-full max-w-2xl max-h-[85vh] flex flex-col justify-between p-6">
            <div className="space-y-4 overflow-y-auto pr-1">
              <div className="flex justify-between items-center border-b border-card-border pb-3">
                <div>
                  <span className="text-[10px] font-bold text-primary-600 uppercase tracking-widest">Chi tiết phiên khám cũ</span>
                  <h3 className="text-base font-bold text-[#2B1D30]">
                    {selectedHistoryConsultation.icd10Name ? `${selectedHistoryConsultation.icd10Code ? `${selectedHistoryConsultation.icd10Code} - ` : ""}${selectedHistoryConsultation.icd10Name}` : selectedHistoryConsultation.diagnosis || "Ca khám trước"}
                  </h3>
                  <p className="text-xs text-[#6A5C70]">
                    Thời gian khám: <strong className="font-mono text-[#2B1D30]">{selectedHistoryConsultation.startedAt ? new Date(selectedHistoryConsultation.startedAt).toLocaleString("vi-VN") : "N/A"}</strong>
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedHistoryConsultation(null)}
                  className="text-gray-400 hover:text-black font-bold text-sm px-2 py-1 rounded-md"
                >
                  ✕
                </button>
              </div>

              {/* Vitals Grid */}
              <div className="border border-slate-200 bg-slate-50/50 p-3.5 rounded-lg space-y-2">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">Chỉ số sinh hiệu</span>
                <div className="grid grid-cols-4 gap-3 text-xs">
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">Huyết áp</span>
                    <strong className="font-mono text-slate-800">{selectedHistoryConsultation.bloodPressure || "N/A"}</strong>
                  </div>
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">Thân nhiệt</span>
                    <strong className="font-mono text-slate-800">{selectedHistoryConsultation.temperature ? `${selectedHistoryConsultation.temperature}°C` : "N/A"}</strong>
                  </div>
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">SpO2</span>
                    <strong className="font-mono text-slate-800">{selectedHistoryConsultation.spo2 ? `${selectedHistoryConsultation.spo2}%` : "N/A"}</strong>
                  </div>
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">Nhịp tim</span>
                    <strong className="font-mono text-slate-800">{selectedHistoryConsultation.heartRate ? `${selectedHistoryConsultation.heartRate} bpm` : "N/A"}</strong>
                  </div>
                </div>
              </div>

              {/* Clinical Symptoms & Notes */}
              <div className="space-y-3">
                <div className="border border-slate-200 p-3.5 bg-white rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Triệu chứng cơ năng</span>
                  <p className="text-xs text-slate-800 font-medium whitespace-pre-wrap">{selectedHistoryConsultation.symptoms || "Không ghi nhận"}</p>
                </div>
                <div className="border border-slate-200 p-3.5 bg-white rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Nhận định & Tóm tắt khám lâm sàng</span>
                  <p className="text-xs text-slate-800 font-medium whitespace-pre-wrap">{selectedHistoryConsultation.clinicalNotes || "Không ghi nhận"}</p>
                </div>
                <div className="border border-slate-200 p-3.5 bg-white rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Chẩn đoán lâm sàng</span>
                  <p className="text-xs text-primary-700 font-bold">{selectedHistoryConsultation.diagnosis || "Chẩn đoán chưa cập nhật"}</p>
                </div>
              </div>

              {/* Linked Prescription Section */}
              {(() => {
                const linkedP = (prescriptionHistory ?? []).find(p => p.consultationId === selectedHistoryConsultation.id);
                return linkedP ? (
                  <div className="border border-purple-200 bg-purple-50/40 p-4 rounded-lg space-y-2.5">
                    <div className="flex items-center justify-between border-b border-purple-200/60 pb-1.5">
                      <span className="text-xs font-bold text-purple-900 flex items-center gap-1.5">
                        <span>Đơn thuốc đính kèm ca khám này</span>
                      </span>
                      <span className="text-[10px] bg-purple-100 text-purple-700 px-2 py-0.5 font-bold uppercase rounded-sm border border-purple-200">
                        {linkedP.status === "CONFIRMED" ? "Đã ký" : linkedP.status === "DISPENSED" ? "Đã cấp phát" : "Nháp"}
                      </span>
                    </div>
                    <ul className="space-y-1.5">
                      {(linkedP.items || []).map((item, idx) => (
                        <li key={item.id || idx} className="text-xs text-slate-700 bg-white p-2 border border-purple-100 rounded-md flex justify-between items-center">
                          <div>
                            <strong className="text-slate-900">{idx + 1}. {item.medicineName}</strong>
                            <p className="text-[11px] text-slate-500 font-medium">{item.dosage} · {item.frequency} · {item.timing}</p>
                          </div>
                          <span className="font-mono text-xs font-bold text-purple-800">x{item.quantity} {item.unit}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : (
                  <div className="p-3 bg-slate-50 border border-slate-200 text-center text-xs text-slate-400 italic rounded-lg">
                    Phiên khám này không có đơn thuốc đính kèm
                  </div>
                );
              })()}
            </div>

            <div className="pt-4 border-t border-card-border flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedHistoryConsultation(null)}
                className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-5 py-2 text-xs transition-all rounded-md"
              >
                ĐÓNG CỬA SỔ
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DETAIL MODAL: Selected History Prescription */}
      {selectedHistoryPrescription && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white border border-card-border shadow-2xl rounded-xl w-full max-w-xl max-h-[85vh] flex flex-col justify-between p-6">
            <div className="space-y-4 overflow-y-auto pr-1">
              <div className="flex justify-between items-center border-b border-card-border pb-3">
                <div>
                  <span className="text-[10px] font-bold text-primary-600 uppercase tracking-widest">Chi tiết đơn thuốc cũ</span>
                  <h3 className="text-base font-bold text-[#2B1D30]">
                    Đơn thuốc · {new Date(selectedHistoryPrescription.createdAt).toLocaleDateString("vi-VN")}
                  </h3>
                  <p className="text-xs text-[#6A5C70]">
                    Mã đơn: <span className="font-mono font-bold text-[#2B1D30]">{selectedHistoryPrescription.id}</span>
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedHistoryPrescription(null)}
                  className="text-gray-400 hover:text-black font-bold text-sm px-2 py-1 rounded-md"
                >
                  ✕
                </button>
              </div>

              {/* Linked Consultation Banner */}
              {(() => {
                const linkedC = (consultationHistory ?? []).find(c => c.id === selectedHistoryPrescription.consultationId);
                return linkedC ? (
                  <div className="border border-emerald-200 bg-emerald-50/50 p-3 rounded-lg flex items-center justify-between text-xs">
                    <div>
                      <span className="text-[10px] font-bold text-emerald-800 uppercase tracking-wider block">Kê cho phiên khám</span>
                      <strong className="text-emerald-950 font-bold">{linkedC.icd10Name ? `${linkedC.icd10Code} - ${linkedC.icd10Name}` : linkedC.diagnosis || "Lần khám trước"}</strong>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        const targetC = linkedC;
                        setSelectedHistoryPrescription(null);
                        setSelectedHistoryConsultation(targetC);
                      }}
                      className="text-[11px] font-bold text-emerald-700 hover:underline bg-emerald-100 border border-emerald-300 px-2.5 py-1 rounded-md"
                    >
                      Xem phiên khám này →
                    </button>
                  </div>
                ) : null;
              })()}

              {/* Medicine List Detail */}
              <div className="space-y-2">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                  Danh sách thuốc trong đơn ({(selectedHistoryPrescription.items || []).length} loại)
                </span>
                <div className="divide-y divide-slate-100 border border-slate-200 rounded-lg">
                  {(selectedHistoryPrescription.items || []).map((item, idx) => (
                    <div key={item.id || idx} className="p-3 bg-white space-y-1">
                      <div className="flex justify-between items-start">
                        <div>
                          <span className="text-xs font-bold text-slate-900">{idx + 1}. {item.medicineName}</span>
                          <span className="text-[10px] font-mono text-slate-400 block">Mã: {item.medicineCode}</span>
                        </div>
                        <span className="text-xs font-bold text-primary-700 font-mono bg-primary-50 px-2 py-0.5 border border-primary-200 rounded-sm">
                          x{item.quantity} {item.unit} ({item.duration || "N/A"} ngày)
                        </span>
                      </div>
                      <p className="text-xs text-slate-600">
                        <strong className="text-slate-700">Liều dùng:</strong> {item.dosage} · {item.frequency} · {item.timing}
                      </p>
                      {item.notes && (
                        <p className="text-[11px] text-amber-800 bg-amber-50 p-1.5 border border-amber-200 rounded-md">
                          Ghi chú: {item.notes}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* General Prescription Notes */}
              {selectedHistoryPrescription.notes && (
                <div className="border border-slate-200 bg-slate-50 p-3 rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Lời dặn chung của bác sĩ</span>
                  <p className="text-xs text-slate-800 italic">{selectedHistoryPrescription.notes}</p>
                </div>
              )}
            </div>

            <div className="pt-4 border-t border-card-border flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedHistoryPrescription(null)}
                className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-5 py-2 text-xs transition-all rounded-md"
              >
                ĐÓNG CỬA SỔ
              </button>
            </div>
          </div>
        </div>
      )}

    </div>

  );
}

