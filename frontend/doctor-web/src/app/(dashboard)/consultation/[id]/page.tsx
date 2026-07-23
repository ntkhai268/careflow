"use client";

import { useEffect, useState, use } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { consultationApi, ConsultationResponse, UpdateConsultationRequest } from "@/lib/consultation-api";
import { prescriptionApi, PrescriptionResponse, PrescriptionItemRequest, MedicineCatalogItem } from "@/lib/prescription-api";
import ConfirmModal from "@/components/ConfirmModal";

// Simple mock dictionary to map patient UUIDs to display names in UI
const MOCK_PATIENTS: Record<string, { name: string; age: number; gender: string }> = {
  "f0000001-0000-0000-0000-000000000001": { name: "Nguyễn Thị Mai", age: 45, gender: "Nữ" },
  "f0000001-0000-0000-0000-000000000002": { name: "Trần Văn Hùng", age: 62, gender: "Nam" },
  "f0000001-0000-0000-0000-000000000003": { name: "Lê Thị Hoa", age: 28, gender: "Nữ" },
  "f0000001-0000-0000-0000-000000000004": { name: "Phạm Đức Anh", age: 7, gender: "Nam" },
  "f0000001-0000-0000-0000-000000000005": { name: "Võ Thị Lan", age: 55, gender: "Nữ" },
};

const MOCK_ICD10 = [
  { code: "K21.9", name: "Bệnh trào ngược dạ dày - thực quản không có viêm thực quản" },
  { code: "I10", name: "Bệnh tăng huyết áp vô căn (nguyên phát)" },
  { code: "E11.9", name: "Bệnh đái tháo đường không phụ thuộc insulin không có biến chứng" },
  { code: "M17.9", name: "Thoái hóa khớp gối không xác định" },
  { code: "J00", name: "Viêm mũi họng cấp (cảm thường)" },
];

export default function ConsultationPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = use(params);
  const consultationId = resolvedParams.id;
  const router = useRouter();
  const { user } = useAuth();

  // Active tab for clinical details
  const [activeTab, setActiveTab] = useState<"vitals" | "clinical" | "diagnosis">("vitals");

  // Custom Modal confirm state
  const [confirmModalConfig, setConfirmModalConfig] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    variant?: "primary" | "warning" | "danger";
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

        // Map mock patient info
        const pInfo = MOCK_PATIENTS[consData.patientId] || { name: "Bệnh nhân thử nghiệm", age: 38, gender: "Nam" };
        setPatientName(pInfo.name);
        setPatientAge(pInfo.age);

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
      } catch (err) {
        console.error(err);
        showToast(err instanceof Error ? err.message : "Không thể tải thông tin phiên khám.", "danger");
      } finally {
        setIsLoading(false);
      }
    }
    loadData();
  }, [consultationId]);

  // Handle ICD-10 Search
  const filteredIcd10 = MOCK_ICD10.filter(item => 
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

    if (editingMedicineCode) {
      setPrescriptionItems(prescriptionItems.map(item => item.medicineCode === editingMedicineCode ? newItem : item));
      showToast(`Đã cập nhật thuốc ${selectedMedicine.name}`);
    } else {
      if (prescriptionItems.some(item => item.medicineCode === selectedMedicine.code)) {
        showToast(`Thuốc ${selectedMedicine.name} đã có trong đơn.`, "warning");
        return;
      }
      setPrescriptionItems([...prescriptionItems, newItem]);
      showToast(`Đã thêm ${selectedMedicine.name} vào đơn thuốc`);
    }

    setIsMedicineDrawerOpen(false);
  };

  const removeMedicine = (code: string, name: string) => {
    setPrescriptionItems(prescriptionItems.filter(item => item.medicineCode !== code));
    showToast(`Đã xóa ${name} khỏi đơn thuốc`, "warning");
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
      message: "Bạn có chắc chắn muốn KÝ và XÁC NHẬN đơn thuốc này? Sau khi xác nhận, đơn thuốc sẽ được chuyển tới Quầy thuốc và không thể sửa đổi.",
      variant: "warning",
      onConfirm: executeConfirmPrescription
    });
  };

  // Action to execute complete consultation
  const executeCompleteConsultation = async () => {
    setIsSaving(true);
    try {
      await saveDraft();
      await consultationApi.completeConsultation(consultationId);
      
      showToast("Hoàn tất lượt khám thành công. Đang chuyển về bảng điều khiển...");
      setTimeout(() => {
        router.replace("/dashboard");
      }, 1500);
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
      return;
    }

    setConfirmModalConfig({
      isOpen: true,
      title: "Hoàn tất Phiên khám",
      message: "Xác nhận hoàn tất phiên khám lâm sàng cho bệnh nhân? Hồ sơ bệnh án sẽ được đóng và lưu trữ.",
      variant: "primary",
      onConfirm: executeCompleteConsultation
    });
  };

  if (isLoading) {
    return (
      <div className="flex h-[calc(100vh-200px)] items-center justify-center">
        <div className="h-8 w-8 animate-spin border-4 border-primary-200 border-t-primary-600 rounded-none" />
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
    <div className="space-y-6 pb-24 relative">
      
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
      <div className="border border-card-border bg-card-bg p-4 shadow-[0_1px_3px_rgba(110,37,130,0.06)] flex flex-wrap justify-between items-center rounded-none">
        <div>
          <span className="text-[10px] font-bold text-primary-600 uppercase tracking-widest">Phiên khám hiện hành</span>
          <h1 className="text-xl font-bold text-[#2B1D30]">{patientName}</h1>
          <p className="text-xs text-[#6A5C70]">{patientAge} tuổi · {MOCK_PATIENTS[consultation?.patientId || ""]?.gender || "Nam"}</p>
        </div>
        <div className="flex gap-4">
          <div className="text-right">
            <span className="text-[10px] uppercase font-bold text-gray-400">Trạng thái khám</span>
            <div className="mt-1">
              <span className={`px-2 py-0.5 text-xs font-semibold rounded-none ${
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
                <span className={`px-2 py-0.5 text-xs font-semibold rounded-none ${
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

      {/* Main two-column workspace */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        
        {/* Left Column (3/5): Tabbed Clinical & Diagnostic Data */}
        <div className="lg:col-span-3 space-y-4">
          
          {/* Navigation Tabs */}
          <div className="border-b border-card-border flex bg-card-bg rounded-none">
            <button
              type="button"
              onClick={() => setActiveTab("vitals")}
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all rounded-none ${
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
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all rounded-none ${
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
              className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all rounded-none ${
                activeTab === "diagnosis"
                  ? "border-primary-600 text-primary-600 bg-[#F8F6F9]"
                  : "border-transparent text-[#6A5C70] hover:text-[#2B1D30]"
              }`}
            >
              3. Chẩn đoán ICD-10
            </button>
          </div>

          {/* TAB 1: Vital Signs with Real-time threshold validation */}
          {activeTab === "vitals" && (
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-none space-y-4">
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
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-none space-y-4">
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
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
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
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
                />
              </div>
            </div>
          )}

          {/* TAB 3: Diagnosis & ICD-10 */}
          {activeTab === "diagnosis" && (
            <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-none space-y-4">
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
                      className="flex-1 border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
                    />
                    {icdSearchQuery && (
                      <button 
                        onClick={() => { setIcdSearchQuery(""); setShowIcdDropdown(false); }}
                        className="border border-card-border px-3 text-xs hover:bg-[#F8F6F9] rounded-none"
                      >
                        Xóa
                      </button>
                    )}
                  </div>

                  {showIcdDropdown && filteredIcd10.length > 0 && (
                    <div className="absolute left-0 right-0 z-20 mt-1 border border-card-border bg-card-bg shadow-md max-h-48 overflow-y-auto rounded-none">
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
                    className="w-full border border-card-border bg-gray-50 px-3 py-2 text-sm text-[#2B1D30] font-mono font-bold focus:outline-none rounded-none"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tên bệnh theo ICD</label>
                  <input
                    type="text"
                    readOnly
                    value={icd10Name}
                    placeholder="Chưa chọn"
                    className="w-full border border-card-border bg-gray-50 px-3 py-2 text-sm text-[#6A5C70] focus:outline-none rounded-none"
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
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
                />
              </div>
            </div>
          )}

        </div>

        {/* Right Column (2/5): Prescription List & Actions */}
        <div className="lg:col-span-2 space-y-6">
          
          {/* Main Prescription Container */}
          <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-none flex flex-col justify-between min-h-[420px]">
            <div>
              <div className="flex justify-between items-center border-b border-card-border pb-3 mb-4">
                <div>
                  <h2 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide">
                    Danh sách Đơn thuốc
                  </h2>
                  <p className="text-[10px] text-[#6A5C70]">Tổng số thuốc: {prescriptionItems.length}</p>
                </div>
                {!isPrescriptionLocked && (
                  <button
                    type="button"
                    onClick={openAddMedicineDrawer}
                    className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-3 py-1.5 text-xs transition-all flex items-center gap-1.5 rounded-none shadow-sm"
                  >
                    + THÊM THUỐC
                  </button>
                )}
              </div>

              {/* Prescription Items List or Empty State */}
              {prescriptionItems.length === 0 ? (
                <div className="border-2 border-dashed border-card-border p-8 text-center bg-[#FAF8FA] space-y-3 rounded-none my-4">
                  <div className="w-10 h-10 border border-primary-300 bg-white flex items-center justify-center mx-auto text-primary-600 font-bold text-lg rounded-none">
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
                      className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-4 py-2 text-xs transition-all rounded-none inline-block mt-2"
                    >
                      + THÊM THUỐC ĐẦU TIÊN
                    </button>
                  )}
                </div>
              ) : (
                <div className="space-y-3 max-h-[320px] overflow-y-auto pr-1">
                  {prescriptionItems.map((item, index) => (
                    <div key={item.medicineCode} className="border border-card-border p-3 bg-white flex justify-between items-start hover:border-primary-400 transition-all rounded-none">
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
                        {item.notes && <p className="text-xs text-amber-700 bg-amber-50 p-1 border border-amber-200 mt-1 rounded-none">Ghi chú: {item.notes}</p>}
                      </div>

                      {!isPrescriptionLocked && (
                        <div className="flex gap-2">
                          <button
                            type="button"
                            onClick={() => openEditMedicineDrawer(item)}
                            className="text-primary-600 hover:text-primary-800 text-xs font-bold underline"
                          >
                            Sửa
                          </button>
                          <button
                            type="button"
                            onClick={() => removeMedicine(item.medicineCode, item.medicineName)}
                            className="text-rose-600 hover:text-rose-800 text-xs font-bold underline"
                          >
                            Xóa
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
                    className="w-full border border-input-border bg-input-bg px-3 py-1.5 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
                  />
                </div>
              )}
            </div>
          </div>

          {/* Follow-up Date Panel */}
          <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] space-y-3 rounded-none">
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
                className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none"
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
          <div className="w-full max-w-lg bg-card-bg border-l border-card-border shadow-2xl h-full flex flex-col justify-between p-6 animate-in slide-in-from-right duration-200 rounded-none">
            
            <div className="space-y-5 overflow-y-auto pr-1">
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
              <div className="relative">
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
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
                />

                {showMedDropdown && filteredMedicines.length > 0 && (
                  <div className="absolute left-0 right-0 z-30 mt-1 border border-card-border bg-white shadow-xl max-h-56 overflow-y-auto rounded-none">
                    {filteredMedicines.map((m) => (
                      <div
                        key={m.code}
                        onClick={() => {
                          setSelectedMedicine(m);
                          setMedSearchQuery(m.name);
                          setShowMedDropdown(false);
                        }}
                        className="px-4 py-2.5 hover:bg-[#F3E8F5] cursor-pointer border-b border-card-border/50 text-sm flex justify-between items-center"
                      >
                        <div>
                          <p className="font-bold text-[#2B1D30]">{m.name}</p>
                          <p className="text-xs text-[#6A5C70]">Mã: <span className="font-mono">{m.code}</span> | Đơn vị: {m.unit}</p>
                        </div>
                        <span className="text-[10px] bg-primary-100 text-primary-700 px-2 py-0.5 font-semibold rounded-none">
                          Khả dụng
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Selected Medicine Info Card */}
              {selectedMedicine && (
                <div className="border border-primary-200 bg-[#F8F6F9] p-3 text-xs space-y-1 rounded-none">
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
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tần suất (*)</label>
                  <input
                    type="text"
                    value={medFrequency}
                    onChange={(e) => setMedFrequency(e.target.value)}
                    placeholder="2 lần / ngày"
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none"
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
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Số ngày</label>
                  <input
                    type="number"
                    value={medDuration}
                    onChange={(e) => setMedDuration(e.target.value)}
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Số lượng (*)</label>
                  <input
                    type="number"
                    value={medQuantity}
                    onChange={(e) => setMedQuantity(e.target.value)}
                    className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none"
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
                      className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none"
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
                className="flex-1 border border-card-border hover:bg-gray-100 text-[#2B1D30] font-bold py-2.5 text-xs transition-all rounded-none"
              >
                HỦY BỎ
              </button>
              <button
                type="button"
                onClick={saveMedicineToPrescription}
                className="flex-1 bg-primary-600 hover:bg-primary-700 text-white font-bold py-2.5 text-xs transition-all rounded-none shadow-sm"
              >
                {editingMedicineCode ? "LƯU CẬP NHẬT" : "XÁC NHẬN THÊM"}
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Sticky Bottom Action Bar */}
      <div className="fixed bottom-0 left-64 right-0 bg-[#2B1D30] border-t border-white/10 h-[64px] px-6 flex justify-between items-center z-30 rounded-none shadow-2xl">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 bg-emerald-400 rounded-none inline-block"></span>
          <span className="text-xs text-white/70 font-medium">Bác sĩ phụ trách: <strong className="text-white">{user?.fullName}</strong></span>
        </div>
        
        <div className="flex gap-3">
          {!isConsultationLocked && (
            <button
              onClick={saveDraft}
              disabled={isSaving}
              className="border border-white/30 hover:bg-white/10 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-none"
            >
              {isSaving ? "ĐANG LƯU..." : "LƯU NHÁP ĐƠN"}
            </button>
          )}
          
          {prescriptionId && prescriptionStatus === "DRAFT" && !isConsultationLocked && (
            <button
              onClick={confirmPrescription}
              disabled={isSaving}
              className="bg-accent hover:bg-orange-600 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-none shadow-sm"
            >
              {isSaving ? "ĐANG KÝ..." : "KÝ & XÁC NHẬN ĐƠN"}
            </button>
          )}

          {!isConsultationLocked ? (
            <button
              onClick={completeConsultation}
              disabled={isSaving}
              className="bg-primary-500 hover:bg-primary-600 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-none shadow-sm"
            >
              {isSaving ? "ĐANG XỬ LÝ..." : "HOÀN TẤT PHIÊN KHÁM"}
            </button>
          ) : (
            <button
              onClick={() => router.replace("/dashboard")}
              className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-6 py-2 text-xs transition-all rounded-none"
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
      />

    </div>
  );
}

