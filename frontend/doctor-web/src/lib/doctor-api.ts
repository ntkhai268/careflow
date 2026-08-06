import { api } from "./api";
import { directoryApi } from "./directory-api";

export interface DoctorProfileResponse {
  doctorId: string;
  doctorName: string;
  departmentCode: string;
  departmentDisplayName: string;
  roomId: string;
  roomDisplayName: string;
}

export const doctorApi = {
  getDoctorProfile: async (doctorId: string): Promise<DoctorProfileResponse> => {
    try {
      // Fetch real doctor profiles from PostgreSQL DB via Directory Service API
      const doctors = await directoryApi.getDoctors();
      const matched = doctors.find((d) => d.id === doctorId || d.userId === doctorId);

      if (matched) {
        return {
          doctorId: matched.id,
          doctorName: matched.title ? `${matched.title} ${matched.fullName}` : matched.fullName,
          departmentCode: matched.departmentCode || "NOI_TONG_QUAT",
          departmentDisplayName: matched.departmentName || "Khoa Nội tổng quát",
          roomId: matched.assignedRoomId || "ROOM-01",
          roomDisplayName: matched.assignedRoomId ? `Phòng ${matched.assignedRoomId}` : "Phòng 101 - Khu A",
        };
      }
    } catch {
      // Fallback if network fails
    }

    // Default fallback
    return {
      doctorId,
      doctorName: "BS. Nguyễn Văn An",
      departmentCode: "NOI_TONG_QUAT",
      departmentDisplayName: "Nội tổng quát",
      roomId: "ROOM-01",
      roomDisplayName: "Phòng 101 - Khu A",
    };
  },
};
