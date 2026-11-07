import { ClinicalSuggestionResponse } from "@/lib/ai-api";

export default function ClinicalCitations({ response }: { response: ClinicalSuggestionResponse }) {
  if (!response.citations || response.citations.length === 0) return null;

  return (
    <div className="mt-3 space-y-3">
      {response.citations.map((c, idx) => (
        <article
          key={`${c.sourceId}-${c.contentHash || idx}`}
          className="rounded-lg border bg-white p-3 text-xs"
        >
          {c.synthetic && <p className="mb-2 font-bold text-amber-800">NGUỒN KIỂM THỬ TỔNG HỢP</p>}
          <a
            href={c.url && c.url.startsWith("https://") ? c.url : undefined}
            target="_blank"
            rel="noopener noreferrer"
            className="font-semibold text-purple-700 underline"
          >
            {c.title} · {c.revision}
          </a>
          <p className="mt-1 text-slate-500">
            Đã rà soát {new Date(c.reviewedAt || Date.now()).toLocaleDateString("vi-VN")} · Mã nội dung{" "}
            {c.contentHash ? c.contentHash.slice(0, 12) : idx}
          </p>
          <blockquote className="mt-2 whitespace-pre-wrap border-l-2 border-purple-300 pl-3">
            {c.quote}
          </blockquote>
        </article>
      ))}
      <p className="text-xs text-slate-500">
        {response.model?.name || "CareFlow AI"} · {response.disclaimer}
      </p>
    </div>
  );
}
