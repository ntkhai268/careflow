import { api, refreshAccessToken } from "./api";
import { createProof } from "./dpop";
import { getAccessToken, getSessionEpoch } from "./session-memory";

export interface UploadedHealthFile { id: string; fileName: string; fileSize: number; contentType?: string }
export interface UploadedHealthRecord {
  id: string; patientId: string; title: string; recordDate: string; facilityName: string; notes?: string;
  bloodPressure?: string; pulse?: number; temperature?: number; bloodSugar?: number;
  heightCm?: number; weightKg?: number; files: UploadedHealthFile[];
}
export const healthRecordApi = {
  list: (patientId: string, signal?: AbortSignal) => api.get<UploadedHealthRecord[]>(`/api/patients/${encodeURIComponent(patientId)}/health-records`, { signal, cache: "no-store" }),
};

/** Authenticated bounded download; ignore response URLs and never expose tokens to a viewer. */
export async function downloadHealthFile(patientId: string, file: UploadedHealthFile, signal: AbortSignal): Promise<Blob> {
  if (![patientId, file.id].every(id => /^[0-9a-fA-F-]{36}$/.test(id))) throw new Error("Mã tệp không hợp lệ.");
  const extension = file.fileName.split(".").pop()?.toLowerCase();
  if (!extension || !["pdf", "png", "jpg", "jpeg"].includes(extension)) throw new Error("Chỉ hỗ trợ PDF, JPEG và PNG.");
  const max = 10 * 1024 * 1024;
  const url = `${process.env.NEXT_PUBLIC_API_URL || "https://api.careflow-demo.online"}/api/patients/${patientId}/health-records/files/${file.id}`;
  for (let attempt = 0; attempt < 2; attempt++) {
    const epoch = getSessionEpoch(), token = getAccessToken();
    if (!token) throw new Error("Cần đăng nhập lại.");
    const response = await fetch(url, { headers: { Authorization: `Bearer ${token}`, DPoP: await createProof("GET", url, token) }, signal, redirect: "error", cache: "no-store" });
    if (getSessionEpoch() !== epoch) { await response.body?.cancel(); throw new Error("Phiên đã thay đổi. Hãy tải lại tệp."); }
    if (response.status === 401 && attempt === 0) { await response.body?.cancel(); await refreshAccessToken(); continue; }
    if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message || `Không tải được tệp (${response.status}).`); }
    const expected = response.headers.get("content-length"), length = expected === null ? null : Number(expected);
    if (length !== null && (!Number.isSafeInteger(length) || length < 1 || length > max)) { await response.body?.cancel(); throw new Error("Dung lượng tệp không hợp lệ hoặc vượt 10MB."); }
    const reader = response.body?.getReader(); if (!reader) throw new Error("Máy chủ chưa trả nội dung tệp.");
    const chunks: Uint8Array<ArrayBuffer>[] = []; let total = 0;
    try {
      while (true) {
        const { value, done } = await reader.read(); if (done) break;
        if (signal.aborted || getSessionEpoch() !== epoch) throw new Error("Phiên đã thay đổi hoặc đã hủy tải.");
        total += value.byteLength; if (total > max) throw new Error("Tệp vượt quá 10MB.");
        chunks.push(new Uint8Array(value));
      }
    } finally { await reader.cancel().catch(() => undefined); }
    if (signal.aborted || getSessionEpoch() !== epoch || total === 0 || (length !== null && length !== total)) throw new Error("Tệp thiếu dữ liệu hoặc phiên đã thay đổi.");
    const type = extension === "pdf" ? "application/pdf" : extension === "png" ? "image/png" : "image/jpeg";
    const blob = new Blob(chunks, { type }); const head = new Uint8Array(await blob.slice(0, 8).arrayBuffer());
    const signature = extension === "pdf" ? [37, 80, 68, 70, 45] : extension === "png" ? [137, 80, 78, 71, 13, 10, 26, 10] : [255, 216, 255];
    if (!signature.every((value, index) => head[index] === value)) throw new Error("Nội dung không khớp định dạng tệp.");
    if (signal.aborted || getSessionEpoch() !== epoch) throw new Error("Phiên đã thay đổi.");
    return blob;
  }
  throw new Error("Cần đăng nhập lại.");
}
