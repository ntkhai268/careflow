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
  queueStatus: "WAITING" | "TICKET_ISSUED" | "CHECKED_IN" | "CALLED" | "IN_PROGRESS" | "COMPLETED" | "MISSED" | "CANCELLED";
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
  type?: "CONSULTATION" | "LAB_EXECUTION" | "PHARMACY_DISPENSING";
  consultationPhase?: "INITIAL" | "RESULT_REVIEW";
  schedulingLane?: "PRIORITY" | "NORMAL" | "RESULT_REVIEW" | null;
  queueClass?: "NORMAL" | "PRIORITY" | null;
  servicePointId?: string | null;
  consultationId?: string | null;
  labOrderId?: string | null;
  prescriptionId?: string | null;
}

export interface QueueDashboardResponse {
  departmentId: string;
  departmentName: string;
  roomCode: string;
  queueDate: string;
  entries: QueueEntry[];
  priorityQueue: QueueEntry[];
  normalQueue: QueueEntry[];
  resultReviewQueue: QueueEntry[];
  recommendedNext: QueueEntry | null;
  lastServedLane: "PRIORITY" | "NORMAL" | "RESULT_REVIEW" | null;
  schedulerVersion: number;
}

export interface ServicePointQueueResponse {
  servicePointId: string;
  queueDate: string;
  entries: QueueEntry[];
  recommendedNext: QueueEntry | null;
}

export interface VisitTicket {
  ticketId: string;
  status: string;
}

export interface CheckInRequest {
  qrToken?: string;
  ticketCode?: string;
  roomId?: string;
  queueClass?: "INITIAL" | "RESULT_REVIEW" | "PRIORITY";
  priorityReasonCode?: string;
}

export interface HospitalCheckInQr {
  siteId: string;
  roomId: string;
  sessionCode: string;
  sessionDate: string;
  qrToken: string;
  expiresAt: string;
}

export const queueApi = {
  getRoomActive: (roomId: string) =>
    api.get<QueueDashboardResponse>(`/api/queues/rooms/${roomId}/active`),

  callNextInRoom: (roomId: string) =>
    api.post<QueueEntry>(`/api/queues/rooms/${roomId}/call-next`, {}),

  getServicePointActive: (servicePointId: string, date?: string) =>
    api.get<ServicePointQueueResponse>(
      `/api/queues/service-points/${servicePointId}/active${date ? `?date=${date}` : ""}`
    ),

  getTicket: (appointmentId: string) =>
    api.get<VisitTicket>(`/api/queues/tickets/appointment/${appointmentId}`),

  callNextAtServicePoint: (servicePointId: string) =>
    api.post<QueueEntry>(`/api/queues/service-points/${servicePointId}/call-next`, {}),

  checkIn: (data: CheckInRequest) =>
    api.post<QueueEntry>(`/api/queues/check-in`, data),

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

  requeueEntry: (entryId: string, reason?: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/requeue`, { reason }),

  getHospitalCheckInQr: (roomId: string, session = "MORNING") =>
    api.get<HospitalCheckInQr>(
      `/api/queues/rooms/${encodeURIComponent(roomId)}/check-in-qr?session=${encodeURIComponent(session)}`,
    ),
};
