"use client";

import { useRef, useState } from "react";
import { VoiceDraftHandle } from "@/components/VoicePrescriptionPanel";
import {
  consultationApi,
  ConsultationResponse,
  UpdateConsultationRequest,
} from "@/lib/consultation-api";
import { PatientAllergyResponse } from "@/lib/patient-api";
import {
  DEFAULT_MEDICINE_CATALOG,
  MedicineCatalogItem,
  prescriptionApi,
  PrescriptionItemRequest,
  PrescriptionResponse,
} from "@/lib/prescription-api";
import {
  prescriptionFieldId,
  prescriptionItemErrors,
  PrescriptionRequiredField,
} from "@/lib/prescription-validation";

interface UsePrescriptionFormParams {
  consultationId: string;
  userId?: string;
  consultation: ConsultationResponse | null;
  patientAllergies: PatientAllergyResponse[];
  temperature: string;
  bloodPressure: string;
  heartRate: string;
  spo2: string;
  height: string;
  weight: string;
  symptoms: string;
  clinicalNotes: string;
  icd10Code: string;
  icd10Name: string;
  diagnosis: string;
  isSaving: boolean;
  setIsSaving: (val: boolean) => void;
  showToast: (message: string, type?: "success" | "danger" | "warning") => void;
  setConfirmModalConfig: React.Dispatch<
    React.SetStateAction<{
      isOpen: boolean;
      title: string;
      message: string;
      variant?: "primary" | "warning" | "danger" | "purple";
      showItemList?: boolean;
      onConfirm: () => void;
    }>
  >;
}

export function usePrescriptionForm({
  consultationId,
  userId,
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
}: UsePrescriptionFormParams) {
  const voiceDraft = useRef<VoiceDraftHandle>(null);
  const savingDraft = useRef(false);

  const [medDetails, setMedDetails] = useState<Record<string, string>>({});
  const [prescriptionId, setPrescriptionId] = useState<string | null>(null);
  const [prescriptionItems, setPrescriptionItems] = useState<PrescriptionItemRequest[]>([]);
  const [prescriptionNotes, setPrescriptionNotes] = useState("");
  const [followUpDate, setFollowUpDate] = useState("");
  const [followUpDepartment, setFollowUpDepartment] = useState("");
  const [followUpTimeSlot, setFollowUpTimeSlot] = useState("");
  const [followUpDelivery, setFollowUpDelivery] = useState<PrescriptionResponse | null>(null);
  const [prescriptionStatus, setPrescriptionStatus] = useState<
    "DRAFT" | "CONFIRMED" | "DISPENSED" | "CANCELLED" | "CANCELLED_BY_AMENDMENT" | null
  >(null);
  const [inheritedDrafts, setInheritedDrafts] = useState<PrescriptionResponse[]>([]);

  const [showPrescriptionNotes, setShowPrescriptionNotes] = useState(false);
  const [showMedNotesInput, setShowMedNotesInput] = useState(false);

  const [isMedicineDrawerOpen, setIsMedicineDrawerOpen] = useState(false);
  const [editingMedicineIndex, setEditingMedicineIndex] = useState<number | null>(null);

  const [medicines, setMedicines] = useState<MedicineCatalogItem[]>(DEFAULT_MEDICINE_CATALOG);
  const [medSearchQuery, setMedSearchQuery] = useState("");
  const [showMedDropdown, setShowMedDropdown] = useState(false);
  const [selectedMedicine, setSelectedMedicine] = useState<MedicineCatalogItem | null>(null);

  const [medDosage, setMedDosage] = useState("1 viên / lần");
  const [medFrequency, setMedFrequency] = useState("2 lần / ngày");
  const [medTiming, setMedTiming] = useState("Sau khi ăn");
  const [medDuration, setMedDuration] = useState("5");
  const [medQuantity, setMedQuantity] = useState("10");
  const [medNotes, setMedNotes] = useState("");

  const withoutReview = (items: PrescriptionItemRequest[]) =>
    items.map((item) => {
      const details = { ...item.details };
      delete details.manualReview;
      return { ...item, details };
    });

  const editPrescriptionItems = (items: PrescriptionItemRequest[]) =>
    setPrescriptionItems(withoutReview(items));

  const openAddMedicineDrawer = () => {
    voiceDraft.current?.collapse();
    setEditingMedicineIndex(null);
    setMedDetails({});
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
    voiceDraft.current?.collapse();
    setEditingMedicineIndex(prescriptionItems.indexOf(item));
    const catalogItem = medicines.find((m) => m.code === item.medicineCode) || {
      code: item.medicineCode || "",
      name: item.medicineName,
      unit: item.unit,
      description: "",
    };
    setSelectedMedicine(catalogItem);
    setMedSearchQuery(item.medicineName);
    setMedDosage(item.dosage ?? "");
    setMedFrequency(item.frequency ?? "");
    setMedTiming(item.timing ?? "");
    setMedDuration(item.duration?.toString() ?? "");
    setMedQuantity(item.quantity?.toString() ?? "");
    setMedDetails(item.details || {});
    setMedNotes(item.notes || "");
    if (item.notes) setShowMedNotesInput(true);
    setIsMedicineDrawerOpen(true);
  };

  const executeSaveMedicine = (newItem: PrescriptionItemRequest) => {
    if (editingMedicineIndex !== null) {
      editPrescriptionItems(
        prescriptionItems.map((item, index) => (index === editingMedicineIndex ? newItem : item))
      );
      showToast(`Đã cập nhật thuốc ${newItem.medicineName}`);
    } else {
      if (prescriptionItems.some((item) => item.medicineCode === newItem.medicineCode)) {
        editPrescriptionItems(
          prescriptionItems.map((item) => (item.medicineCode === newItem.medicineCode ? newItem : item))
        );
        showToast(
          `Thuốc ${newItem.medicineName} đã có trong đơn. Hệ thống đã cập nhật lại số lượng và liều dùng mới.`,
          "warning"
        );
        setIsMedicineDrawerOpen(false);
        return;
      }
      editPrescriptionItems([...prescriptionItems, newItem]);
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
      duration: medDuration.trim() ? Number(medDuration) : null,
      quantity: medQuantity.trim() ? Number(medQuantity) : null,
      notes: medNotes,
      details: medDetails,
    };

    const criticalConflict = patientAllergies.find(
      (a) =>
        a.severity === "CRITICAL" &&
        (selectedMedicine.name.toLowerCase().includes(a.allergyName.toLowerCase()) ||
          (a.allergyGroup &&
            a.allergyGroup.toLowerCase() === "beta-lactam" &&
            ["MED002", "MED014"].includes(selectedMedicine.code)) ||
          (a.allergyName.toLowerCase().includes("penicillin") &&
            ["MED002", "MED014"].includes(selectedMedicine.code)))
    );

    if (criticalConflict) {
      setConfirmModalConfig({
        isOpen: true,
        title: "CẢNH BÁO NGUY HIỂM CAO - PHẢN VỆ Y KHOA",
        message: `Thuốc "${selectedMedicine.name}" chứa thành phần thuộc nhóm [${criticalConflict.allergyGroup || criticalConflict.allergyName}]. Bệnh nhân có tiền sử DỊ ỨNG NẶNG (${criticalConflict.reaction || "Nguy cơ sốc phản vệ"}). Bác sĩ có chắc chắn muốn tiếp tục kê đơn thuốc này không?`,
        variant: "danger",
        onConfirm: () => {
          executeSaveMedicine(newItem);
          setConfirmModalConfig((prev) => ({ ...prev, isOpen: false }));
        },
      });
      return;
    }

    executeSaveMedicine(newItem);
  };

  const removeMedicine = (index: number, name: string) => {
    const updatedItems = prescriptionItems.filter((_, i) => i !== index);
    editPrescriptionItems(updatedItems);
    showToast(`Đã xóa ${name} khỏi đơn thuốc`, "warning");
  };

  const clearAllMedicines = () => {
    editPrescriptionItems([]);
    showToast("Đã xóa toàn bộ đơn thuốc nháp", "warning");
  };

  const saveDraft = async () => {
    if (!userId || isSaving || savingDraft.current) return false;
    if (
      followUpDate &&
      prescriptionItems.length > 0 &&
      (!prescriptionId || prescriptionStatus === "DRAFT") &&
      (!followUpDepartment || !followUpTimeSlot)
    ) {
      showToast("Chọn khoa và ca tái khám còn chỗ trước khi lưu toa.", "warning");
      return false;
    }
    savingDraft.current = true;
    setIsSaving(true);

    try {
      await voiceDraft.current?.stop();
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
        diagnosis,
      };
      await consultationApi.updateConsultation(consultationId, consultationUpdate);

      if (
        prescriptionId &&
        prescriptionStatus === "DRAFT" &&
        prescriptionItems.length > 0 &&
        voiceDraft.current?.pending()
      ) {
        await prescriptionApi.updatePrescription(prescriptionId, {
          consultationId,
          patientId: consultation!.patientId,
          doctorId: userId,
          diagnosis,
          notes: prescriptionNotes,
          followUpDate: followUpDate || undefined,
          followUpDepartment: followUpDate ? followUpDepartment : undefined,
          followUpTimeSlot: followUpDate ? followUpTimeSlot : undefined,
          items: prescriptionItems,
        });
      }
      const fromVoice = await voiceDraft.current?.save(
        !!prescriptionId && prescriptionStatus === "DRAFT" && prescriptionItems.length === 0
      );
      const targetId = fromVoice?.id || prescriptionId;
      const targetItems = fromVoice ? withoutReview(fromVoice.items) : prescriptionItems;
      if (fromVoice) {
        setPrescriptionId(fromVoice.id);
        setPrescriptionStatus(fromVoice.status);
        setPrescriptionItems(targetItems);
      }
      if (targetId && targetItems.length === 0) {
        throw new Error(
          "Toa nháp cần ít nhất một thuốc. Thêm thuốc hoặc hủy toa, không tạo lại mã toa."
        );
      }
      if (targetItems.length > 0 && (!targetId || (fromVoice?.status || prescriptionStatus) === "DRAFT")) {
        const prescriptionData = {
          consultationId,
          patientId: consultation!.patientId,
          doctorId: userId,
          diagnosis,
          notes: prescriptionNotes,
          followUpDate: followUpDate || undefined,
          followUpDepartment: followUpDate ? followUpDepartment : undefined,
          followUpTimeSlot: followUpDate ? followUpTimeSlot : undefined,
          items: targetItems,
        };

        if (targetId) {
          const res = await prescriptionApi.updatePrescription(targetId, prescriptionData);
          setPrescriptionStatus(res.data.status);
        } else {
          const res = await prescriptionApi.createPrescription(prescriptionData);
          setPrescriptionId(res.data.id);
          setPrescriptionStatus(res.data.status);
        }
      }

      showToast("Lưu thông tin nháp thành công.");
      return true;
    } catch (err) {
      console.error(err);
      showToast(err instanceof Error ? err.message : "Đã xảy ra lỗi khi lưu thông tin.", "danger");
      return false;
    } finally {
      savingDraft.current = false;
      setIsSaving(false);
    }
  };

  const focusMissingPrescriptionField = () => {
    for (let index = 0; index < prescriptionItems.length; index++) {
      const errors = prescriptionItemErrors(prescriptionItems[index]);
      const key = Object.keys(errors)[0] as PrescriptionRequiredField | undefined;
      if (!key) continue;
      const input = document.getElementById(prescriptionFieldId(index, key));
      const editor = input?.closest("details");
      if (editor) editor.open = true;
      input?.scrollIntoView({ behavior: "smooth", block: "center" });
      input?.focus({ preventScroll: true });
      return { index, message: errors[key] };
    }
    return null;
  };

  const executeConfirmPrescription = async () => {
    if (!prescriptionId || isSaving) return;
    if (voiceDraft.current?.pending()) {
      setConfirmModalConfig((previous) => ({ ...previous, isOpen: false }));
      voiceDraft.current.open("Lưu đoạn ghi âm mới và kiểm tra các thuốc vừa thêm trước khi ký.");
      return;
    }
    if (!(await saveDraft())) return;
    setIsSaving(true);
    try {
      const res = await prescriptionApi.confirmPrescription(prescriptionId);
      setPrescriptionStatus(res.data.status);
      setFollowUpDelivery(res.data);
      showToast(
        res.data.followUpDate && res.data.followUpStatus !== "SCHEDULED"
          ? "Toa đã ký. Lịch tái khám chưa được xác nhận; kiểm tra trạng thái và thử lại."
          : "Toa đã ký; hệ thống đang chuyển thông tin đến quầy thuốc.",
        res.data.followUpDate && res.data.followUpStatus !== "SCHEDULED" ? "warning" : "success"
      );
    } catch (err) {
      const message = err instanceof Error ? err.message : "Đã xảy ra lỗi khi ký đơn thuốc.";
      showToast(message, "danger");
      voiceDraft.current?.open(message);
      requestAnimationFrame(() => requestAnimationFrame(() => focusMissingPrescriptionField()));
    } finally {
      setIsSaving(false);
      setConfirmModalConfig((prev) => ({ ...prev, isOpen: false }));
    }
  };

  const confirmPrescription = () => {
    if (!prescriptionId) {
      showToast("Vui lòng thêm thuốc và lưu nháp đơn trước khi ký xác nhận.", "warning");
      return;
    }
    if (voiceDraft.current?.pending()) {
      voiceDraft.current.open("Lưu đoạn ghi âm mới và kiểm tra các thuốc vừa thêm trước khi ký.");
      return;
    }

    const missing = focusMissingPrescriptionField();
    if (missing) {
      showToast(
        `Thuốc ${missing.index + 1}: ${missing.message} Các ô cần bổ sung đã được đánh dấu đỏ.`,
        "warning"
      );
      return;
    }

    if (
      prescriptionItems.some(
        (item) =>
          !item.medicineName?.trim() ||
          !item.unit?.trim() ||
          item.details?.manualReview !== "confirmed"
      )
    ) {
      showToast(
        "Kiểm tra các dòng thuốc rồi xác nhận toàn bộ đơn thuốc ở cuối danh sách trước khi ký.",
        "warning"
      );
      document.getElementById("prescription-review")?.scrollIntoView({ behavior: "smooth", block: "center" });
      document.getElementById("prescription-review-confirm")?.focus({ preventScroll: true });
      return;
    }

    setConfirmModalConfig({
      isOpen: true,
      title: "Ký & Xác nhận Đơn thuốc",
      message:
        "Bác sĩ có chắc chắn muốn KÝ và XÁC NHẬN đơn thuốc này? Sau khi xác nhận, đơn thuốc sẽ được chuyển tới Quầy thuốc và không thể sửa đổi.",
      variant: "purple",
      showItemList: true,
      onConfirm: executeConfirmPrescription,
    });
  };

  return {
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
  };
}
