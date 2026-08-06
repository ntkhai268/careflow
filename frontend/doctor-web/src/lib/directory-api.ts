import { api } from "./api";

export interface DepartmentItem {
  code: string;
  id?: string;
  name: string;
  description?: string;
  isActive?: boolean;
}

export interface RoomItem {
  id: string;
  departmentCode: string;
  displayName: string;
  roomType: string;
  isActive?: boolean;
}

export interface DoctorProfileItem {
  id: string;
  userId: string;
  fullName: string;
  title?: string;
  departmentCode?: string;
  departmentName?: string;
  assignedRoomId?: string;
  specialization?: string;
  licenseNumber?: string;
  isActive?: boolean;
}

export interface CreateDepartmentPayload {
  code: string;
  name: string;
  description?: string;
  isActive?: boolean;
}

export interface UpdateDepartmentPayload {
  name: string;
  description?: string;
  isActive?: boolean;
}

export interface CreateRoomPayload {
  id: string;
  departmentCode: string;
  displayName: string;
  roomType: string;
  isActive?: boolean;
}

export interface UpdateRoomPayload {
  departmentCode: string;
  displayName: string;
  roomType: string;
  isActive?: boolean;
}

export interface CreateDoctorProfilePayload {
  userId: string;
  fullName: string;
  title?: string;
  departmentCode?: string;
  assignedRoomId?: string;
  specialization?: string;
  licenseNumber?: string;
  isActive?: boolean;
}

export interface UpdateDoctorProfilePayload {
  fullName: string;
  title?: string;
  departmentCode?: string;
  assignedRoomId?: string;
  specialization?: string;
  licenseNumber?: string;
  isActive?: boolean;
}

export const directoryApi = {
  // Departments
  getDepartments: async (): Promise<DepartmentItem[]> => {
    const res = await api.get<DepartmentItem[]>("/api/directory/departments");
    return res.data ?? [];
  },
  createDepartment: async (payload: CreateDepartmentPayload): Promise<DepartmentItem> => {
    const res = await api.post<DepartmentItem>("/api/directory/departments", payload);
    return res.data;
  },
  updateDepartment: async (code: string, payload: UpdateDepartmentPayload): Promise<DepartmentItem> => {
    const res = await api.put<DepartmentItem>(`/api/directory/departments/${code}`, payload);
    return res.data;
  },

  // Rooms
  getRooms: async (departmentCode?: string, roomType?: string): Promise<RoomItem[]> => {
    const params = new URLSearchParams();
    if (departmentCode) params.append("departmentCode", departmentCode);
    if (roomType) params.append("roomType", roomType);
    const queryString = params.toString() ? `?${params.toString()}` : "";
    const res = await api.get<RoomItem[]>(`/api/directory/rooms${queryString}`);
    return res.data ?? [];
  },
  createRoom: async (payload: CreateRoomPayload): Promise<RoomItem> => {
    const res = await api.post<RoomItem>("/api/directory/rooms", payload);
    return res.data;
  },
  updateRoom: async (id: string, payload: UpdateRoomPayload): Promise<RoomItem> => {
    const res = await api.put<RoomItem>(`/api/directory/rooms/${id}`, payload);
    return res.data;
  },

  // Doctor Profiles
  getDoctors: async (departmentCode?: string): Promise<DoctorProfileItem[]> => {
    const queryString = departmentCode ? `?departmentCode=${departmentCode}` : "";
    const res = await api.get<DoctorProfileItem[]>(`/api/directory/doctors${queryString}`);
    return res.data ?? [];
  },
  createDoctorProfile: async (payload: CreateDoctorProfilePayload): Promise<DoctorProfileItem> => {
    const res = await api.post<DoctorProfileItem>("/api/directory/doctors", payload);
    return res.data;
  },
  updateDoctorProfile: async (id: string, payload: UpdateDoctorProfilePayload): Promise<DoctorProfileItem> => {
    const res = await api.put<DoctorProfileItem>(`/api/directory/doctors/${id}`, payload);
    return res.data;
  },
};
