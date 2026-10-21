import { api } from "./api";
export interface Timing { samples: number; meanMinutes: number | null; p50Minutes: number | null; p95Minutes: number | null }
export interface Metrics { booked:number; arrived:number; scheduledArrivals:number; walkIns:number; completed:number; cancelledBookings:number; cancellationPercent:number|null; missedVisits:number; missedCalls:number; reviewQueueEntries:number; initialWait:Timing; reviewWait:Timing; consultationDuration:Timing; excludedWaitSamples:number; excludedDurationSamples:number; consultationsMissingContext:number }
export interface SyncState { source:string; status:string; last_success_at:string|null; last_error_code:string|null; last_row_count:number|null }
export interface AnalyticsReport { from:string; to:string; timezone:string; definitionVersion:string; summary:Metrics; daily:{date:string;metrics:Metrics}[]; groups:{departmentId:string|null;departmentCode:string|null;roomId:string|null;metrics:Metrics}[]; metadata:{dataset:string;recordCount:number;generatedAt:string;first_observed_at:string|null;last_observed_at:string|null;rejected_snapshots:number;last_event_received_at?:string|null;missing_source_events?:number;sources:SyncState[];coverageNote:string} }
export interface ReportFilter { from:string;to:string;departmentId:string;roomId:string }
export const analyticsApi = {
  report: async (filter:ReportFilter) => {
    const query=new URLSearchParams({from:filter.from,to:filter.to});
    if(filter.departmentId)query.set("departmentId",filter.departmentId);
    if(filter.roomId)query.set("roomId",filter.roomId);
    const response=await api.get<AnalyticsReport>(`/api/analytics/report?${query}`);
    if(!response.data || !Array.isArray(response.data.daily) || !response.data.metadata)throw new Error("Dữ liệu báo cáo không hợp lệ.");
    return response.data;
  },
  reconcile: async () => (await api.post<SyncState[]>("/api/analytics/reconcile",{})).data,
  rebuild: async () => (await api.post<{rebuiltRecords:number}>("/api/analytics/rebuild",{})).data,
};
