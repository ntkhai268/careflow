import { ConsultationResponse } from "@/lib/consultation-api";
import { PatientAllergyResponse } from "@/lib/patient-api";
import { PrescriptionItemRequest } from "@/lib/prescription-api";

export interface Icd10CatalogItem {
  code: string;
  name: string;
}

export const ICD10_CATALOG: Icd10CatalogItem[] = [
  { code: "K21.9", name: "Bệnh trào ngược dạ dày - thực quản không có viêm thực quản" },
  { code: "I10", name: "Bệnh tăng huyết áp vô căn (nguyên phát)" },
  { code: "E11.9", name: "Bệnh đái tháo đường không phụ thuộc insulin không có biến chứng" },
  { code: "M17.9", name: "Thoái hóa khớp gối không xác định" },
  { code: "J00", name: "Viêm mũi họng cấp (cảm thường)" },
  { code: "J18.9", name: "Viêm phổi không xác định" },
  { code: "K29.7", name: "Viêm dạ dày không xác định" },
];

export const CONSULTATION_STATUS_LABELS: Record<string, string> = {
  IN_PROGRESS: "Đang khám",
  AWAITING_CLS: "Chờ cận lâm sàng",
  AWAITING_REVIEW: "Chờ đọc kết quả",
  READY_TO_COMPLETE: "Sẵn sàng hoàn tất",
  COMPLETED: "Hoàn tất",
  CANCELLED: "Đã hủy",
  TRANSFERRED: "Đã chuyển",
};

export interface VitalWarning {
  text: string;
  level: "danger" | "warning";
}

export function getTempWarning(val: string): VitalWarning | null {
  const num = parseFloat(val);
  if (!num) return null;
  if (num > 38.5) return { text: "Sốt cao", level: "danger" };
  if (num > 37.5) return { text: "Sốt nhẹ", level: "warning" };
  if (num < 36.0) return { text: "Hạ thân nhiệt", level: "warning" };
  return null;
}

export function getBpWarning(val: string): VitalWarning | null {
  if (!val) return null;
  const parts = val.split("/");
  if (parts.length === 2) {
    const sys = parseInt(parts[0]);
    const dia = parseInt(parts[1]);
    if (sys >= 140 || dia >= 90) return { text: "Huyết áp cao", level: "danger" };
    if (sys <= 90 || dia <= 60) return { text: "Huyết áp thấp", level: "warning" };
  }
  return null;
}

export function getHeartRateWarning(val: string): VitalWarning | null {
  const num = parseInt(val);
  if (!num) return null;
  if (num > 100) return { text: "Nhịp tim nhanh", level: "warning" };
  if (num < 60) return { text: "Nhịp tim chậm", level: "warning" };
  return null;
}

export function getSpo2Warning(val: string): VitalWarning | null {
  const num = parseInt(val);
  if (!num) return null;
  if (num < 95) return { text: "Cảnh báo thiếu Oxy", level: "danger" };
  return null;
}

export function buildDemoFallbackData(consultationId: string): {
  consultation: ConsultationResponse;
  patientName: string;
  patientAge: number;
  patientGender: string;
  patientIdCode: string;
  patientAllergies: PatientAllergyResponse[];
  patientMedicalHistory: string;
  prescriptionItems: PrescriptionItemRequest[];
  prescriptionNotes: string;
} {
  const now = new Date().toISOString();
  const consultation: ConsultationResponse = {
    id: consultationId || "DEMO-CONSULTATION-01",
    patientId: "p-demo-001",
    doctorId: "doc-demo-01",
    temperature: 38.5,
    bloodPressure: "145/95",
    heartRate: 88,
    spo2: 96,
    height: 168,
    weight: 65,
    symptoms:
      "Sốt cao 38.5°C 3 ngày nay, ho có đờm vàng đặc, khó thở nhẹ khi gắng sức, tức ngực trái.",
    clinicalNotes:
      "Bệnh nhân có tiền sử Tăng huyết áp 5 năm, Đái tháo đường Type 2, dị ứng Penicillin. Hiện tỉnh, tiếp xúc tốt, phổi ran ẩm rải rác.",
    icd10Code: "J18.9",
    icd10Name: "Viêm phổi, không đặc hiệu",
    diagnosis: "Theo dõi Viêm phổi mắc phải tại cộng đồng (CAP) / Tăng huyết áp độ 2",
    status: "IN_PROGRESS",
    startedAt: now,
    createdAt: now,
    updatedAt: now,
  };

  return {
    consultation,
    patientName: "Nguyễn Văn An",
    patientAge: 58,
    patientGender: "Nam",
    patientIdCode: "DEMO-001",
    patientAllergies: [
      {
        id: "alg-1",
        allergyName: "Penicillin",
        allergyGroup: "Kháng sinh nhóm Beta-lactam",
        severity: "CRITICAL",
        reaction: "Phát ban, phù mạch, khó thở",
      },
    ],
    patientMedicalHistory:
      "Tăng huyết áp 5 năm điều trị thường xuyên, Đái tháo đường Type 2 kiểm soát bằng Metformin.",
    prescriptionItems: [
      {
        medicineName: "Clopidogrel 75mg",
        medicineCode: "CLOP-75",
        unit: "Viên",
        dosage: "1 viên/lần",
        frequency: "1 lần/ngày",
        timing: "Sau ăn sáng",
        duration: 30,
        quantity: 30,
        notes: "Thuốc chống kết tập tiểu cầu",
      },
      {
        medicineName: "Omeprazole 20mg",
        medicineCode: "OMEP-20",
        unit: "Viên",
        dosage: "1 viên/lần",
        frequency: "1 lần/ngày",
        timing: "Trước ăn sáng 30 phút",
        duration: 14,
        quantity: 14,
        notes: "Giảm tiết acid dạ dày",
      },
    ],
    prescriptionNotes: "Uống thuốc đúng giờ, tái khám sau 7 ngày.",
  };
}
