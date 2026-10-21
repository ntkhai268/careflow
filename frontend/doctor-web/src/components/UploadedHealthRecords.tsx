"use client";
import { useEffect, useRef, useState } from "react";
import { healthRecordApi, downloadHealthFile, UploadedHealthRecord, UploadedHealthFile } from "@/lib/health-record-api";
import { getErrorMessage } from "@/lib/error-utils";
import { getSessionEpoch } from "@/lib/session-memory";

export default function UploadedHealthRecords({ patientId }: { patientId: string }) {
  const [records, setRecords] = useState<UploadedHealthRecord[] | null>(null);
  const [error, setError] = useState<string | null>(null), [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const download = useRef<AbortController | null>(null);
  useEffect(() => {
    const request = new AbortController(); setRecords(null); setError(null);
    void healthRecordApi.list(patientId, request.signal).then(result => {
      if (!Array.isArray(result.data) || result.data.some(row => row.patientId !== patientId)) throw new Error("Hồ sơ tải lên không khớp bệnh nhân.");
      if (!request.signal.aborted) setRecords(result.data);
    }).catch(failure => { if (!request.signal.aborted) setError(getErrorMessage(failure, "Chưa tải được hồ sơ; không thể kết luận không có bệnh sử.")); });
    return () => { request.abort(); download.current?.abort(); };
  }, [patientId, retry]);
  async function saveFile(file: UploadedHealthFile) {
    if (download.current) return;
    const request = new AbortController(); download.current = request; setBusy(file.id); setError(null);
    try {
      const epoch = getSessionEpoch();
      const blob = await downloadHealthFile(patientId, file, request.signal);
      if (request.signal.aborted) return;
      if (epoch !== getSessionEpoch()) throw new Error("Phiên đã thay đổi. Hãy tải lại tệp.");
      const url = URL.createObjectURL(blob), anchor = document.createElement("a");
      try {
        anchor.href = url; anchor.download = file.fileName.split(/[\\/]/).pop()?.replace(/[\x00-\x1f]/g, "_") || "ho-so"; anchor.rel = "noopener";
        if (epoch !== getSessionEpoch()) throw new Error("Phiên đã thay đổi.");
        document.body.appendChild(anchor); anchor.click();
      } finally { anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
    } catch (failure) { if (!request.signal.aborted) setError(getErrorMessage(failure, "Không tải được tệp. Hãy thử lại.")); }
    finally { if (download.current === request) { download.current = null; setBusy(null); } }
  }
  return <section className="rounded-xl border border-purple-100 bg-white p-4 space-y-3">
    <div className="flex items-center justify-between gap-3"><h3 className="font-semibold">Hồ sơ bệnh nhân tải lên</h3><button className="rounded-lg border px-3 py-1 text-sm" onClick={() => setRetry(value => value + 1)}>Tải lại hồ sơ</button></div>
    <p className="text-xs text-gray-500">Tài liệu do bệnh nhân cung cấp, cần đối chiếu khi khám. PDF, JPEG hoặc PNG được tải bằng phiên đăng nhập hiện tại.</p>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {records === null && !error && <p role="status">Đang tải hồ sơ…</p>}
    {records?.length === 0 && <p className="text-sm text-gray-600">Bệnh nhân chưa tải hồ sơ lên.</p>}
    {records?.map(record => <details key={record.id} className="rounded-lg border p-3">
      <summary className="cursor-pointer font-medium">{record.title} · {record.recordDate} · {record.facilityName}</summary>
      <div className="mt-3 space-y-3 text-sm"><p className="whitespace-pre-wrap">{record.notes || "Chưa có ghi chú."}</p>
        <dl className="grid grid-cols-2 gap-2">{[["Huyết áp", record.bloodPressure], ["Mạch", record.pulse], ["Nhiệt độ", record.temperature], ["Đường huyết", record.bloodSugar], ["Chiều cao (cm)", record.heightCm], ["Cân nặng (kg)", record.weightKg]].filter(([, value]) => value !== undefined && value !== null && value !== "").map(([label, value]) => <div key={String(label)}><dt className="text-gray-500">{label}</dt><dd>{value}</dd></div>)}</dl>
        {(record.files || []).map(file => <div key={file.id} className="flex flex-wrap items-center gap-2"><span className="break-all">{file.fileName}</span><button disabled={busy !== null} className="rounded-lg border border-purple-200 px-3 py-1 text-purple-800 disabled:opacity-50" onClick={() => void saveFile(file)}>{busy === file.id ? "Đang tải…" : "Tải tệp để xem"}</button>{busy === file.id && <button onClick={() => download.current?.abort()} className="rounded-lg border px-3 py-1">Hủy tải</button>}</div>)}
      </div>
    </details>)}
  </section>;
}
