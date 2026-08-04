import { api } from "./api";

export interface QueueEntry {
  entryId: string;
  appointmentId: string;
  patientId: string;
  userId: string;
  departmentId: string;
  departmentName: string;
  roomCode: string;
  queueDate: string;
  queueNumber: string;
  priorityLevel: "APPOINTMENT" | "PRIORITY" | "EMERGENCY" | "WALK_IN" | "RESULT_REVIEW";
  queueStatus: "CHECKED_IN" | "CALLED" | "IN_PROGRESS" | "COMPLETED" | "MISSED";
  effectivePosition: number | null;
  estimatedWaitMinutes: number | null;
  callAttempts: number;
  missedCount: number;
  calledByUserId: string | null;
  scheduledStartAt: string;
  checkedInAt: string | null;
  calledAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  missedAt: string | null;
}

export interface QueueDashboardResponse {
  departmentId: string;
  departmentName: string;
  roomCode: string;
  queueDate: string;
  entries: QueueEntry[];
  recommendedNext: QueueEntry | null;
}

export const queueApi = {
  getRoomActive: (roomId: string) =>
    api.get<QueueDashboardResponse>(`/api/queues/rooms/${roomId}/active`),

  callNextInRoom: (roomId: string) =>
    api.post<QueueEntry>(`/api/queues/rooms/${roomId}/call-next`, {}),

  callEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/call`, {}),

  recallEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/recall`, {}),

  missEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/miss`, {}),

  startEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/start`, {}),

  completeEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/complete`, {}),
};
