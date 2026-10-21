import { api } from "./api";
export interface ScheduleWindow { start: string; end: string; slotMinutes: number; capacity: number }
export interface ScheduleDefinition { effectiveFrom: string; timeZone: string; weekdays: number[]; windows: ScheduleWindow[]; exceptions: { date: string; closed: boolean; windows: ScheduleWindow[] }[] }
export interface RoomSchedule { roomId: string; version: number; definition: ScheduleDefinition; history: { version: number; effectiveFrom: string; createdAt: string }[] }
export interface RoomPolicy { version: number; maxCallAttempts: number; schedulerPolicy: string; config: { avgConsultationMinutes: number; nearTurnThreshold: number; missedPolicy: "REQUEUE_FRONT" | "REQUEUE_BACK" | "REQUIRE_MANUAL"; active: boolean } }
export const scheduleApi = {
  get: (room: string) => api.get<RoomSchedule>(`/api/appointments/schedules/rooms/${encodeURIComponent(room)}`),
  save: (room: string, expectedVersion: number, definition: ScheduleDefinition) => api.put<RoomSchedule>(`/api/appointments/schedules/rooms/${encodeURIComponent(room)}`, { expectedVersion, definition }),
  policy: (room: string) => api.get<RoomPolicy>(`/api/queues/rooms/${encodeURIComponent(room)}/config`),
  savePolicy: (room: string, policy: RoomPolicy) => api.put<RoomPolicy>(`/api/queues/rooms/${encodeURIComponent(room)}/config`, { expectedVersion: policy.version, ...policy.config }),
};
