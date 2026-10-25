import { api } from './api';
import { QueueEntry } from './queue-api';
export interface ReceptionPatient { id: string; userId: string | null; fullName: string; dateOfBirth: string | null; gender: string | null; maskedPhone?: string | null }
export interface ReceptionCreate { roomId: string; fullName: string; dateOfBirth?: string; gender?: string; phone?: string; idCardNumber?: string }
export interface Intake { patientId: string; departmentId: string; roomId: string; serviceCode: string; priorityLevel: 'WALK_IN' }
export const receptionApi = {
  search: (roomId: string, query: string) => api.post<ReceptionPatient[]>('/api/patients/reception/search', { roomId, query }),
  create: (data: ReceptionCreate, key: string) => api.post<ReceptionPatient>('/api/patients/reception', data, { headers: { 'Idempotency-Key': key } }),
  minimum: (id: string, room: string) => api.get<ReceptionPatient>(`/api/patients/reception/${id}?roomId=${encodeURIComponent(room)}`),
  intake: (data: Intake, key: string) => api.post<QueueEntry>('/api/queues/entries', data, { headers: { 'Idempotency-Key': key } }),
};
