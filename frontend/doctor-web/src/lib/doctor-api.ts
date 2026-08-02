import { api } from "./api";

export interface DoctorProfileResponse {
  doctorId: string;
  doctorName: string;
  departmentCode: string;
  departmentDisplayName: string;
  roomId: string;
  roomDisplayName: string;
}

// Map mock cho các Bác sĩ test để xác định Chuyên khoa & Phòng khám tương ứng
const MOCK_DOCTOR_PROFILES: Record<string, DoctorProfileResponse> = {
  // BS. Nguyễn Văn An
  "d0000001-0000-0000-0000-000000000001": {
    doctorId: "d0000001-0000-0000-0000-000000000001",
    doctorName: "BS. Nguyễn Văn An",
    departmentCode: "NOI_TONG_QUAT",
    departmentDisplayName: "Nội tổng quát",
    roomId: "ROOM-01",
    roomDisplayName: "Phòng 101 - Khu A"
  },
  // BS. Phạm Hoàng Nam
  "11111111-1111-1111-1111-111111111111": {
    doctorId: "11111111-1111-1111-1111-111111111111",
    doctorName: "BS. Phạm Hoàng Nam",
    departmentCode: "NOI_TONG_QUAT",
    departmentDisplayName: "Nội tổng quát",
    roomId: "ROOM-02",
    roomDisplayName: "Phòng 102 - Khu A"
  }
};

export const doctorApi = {
  getDoctorProfile: async (doctorId: string): Promise<DoctorProfileResponse> => {
    if (MOCK_DOCTOR_PROFILES[doctorId]) {
      return MOCK_DOCTOR_PROFILES[doctorId];
    }
    return {
      doctorId,
      doctorName: "Bác sĩ",
      departmentCode: "NOI_TONG_QUAT",
      departmentDisplayName: "Nội tổng quát",
      roomId: "ROOM-01",
      roomDisplayName: "Phòng 101 - Khu A"
    };
  }
};
