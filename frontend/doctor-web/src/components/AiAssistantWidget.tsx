"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { api } from "@/lib/api";
import { aiApi, ClinicalSuggestionResponse } from "@/lib/ai-api";
import ClinicalCitations from "@/components/ClinicalCitations";
import { FormattedAiText } from "@/lib/format-ai-text";

export default function AiAssistantWidget() {
  const { user } = useAuth();
  const path = usePathname();
  if (user?.role?.toUpperCase() !== "DOCTOR") return null;
  const id = /^\/consultation\/([a-fA-F0-9-]{36})$/.exec(path)?.[1];
  return <Assistant key={`${user.id}:${id || "none"}`} consultationId={id} />;
}

function Assistant({ consultationId }: { consultationId?: string }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [messages, setMessages] = useState<{ question: string; response: ClinicalSuggestionResponse }[]>([]);
  const active = useRef(true);
  const sequence = useRef(0);

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      sequence.current++;
    };
  }, []);

  useEffect(() => {
    if (!open || !consultationId) return;
    let valid = true;
    api
      .get<{ question: string; response: ClinicalSuggestionResponse }[]>(
        `/api/ai/consultations/${consultationId}/interactions`
      )
      .then(
        (r) => {
          if (valid && active.current) setMessages([...r.data].reverse());
        },
        (e) => {
          if (valid && active.current) setError(e instanceof Error ? e.message : "Chưa tải được lịch sử trợ lý.");
        }
      );
    return () => {
      valid = false;
    };
  }, [open, consultationId]);

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!consultationId || busy || !text.trim()) return;
    const question = text.trim();
    const ticket = ++sequence.current;
    setBusy(true);
    setError("");
    try {
      const r = await aiApi.sendClinicalChat(consultationId, question, messages.at(-1)?.response.interactionId);
      if (active.current && ticket === sequence.current) {
        setMessages((m) => [...m, { question, response: r.data }]);
        setText("");
      }
    } catch (e) {
      if (active.current && ticket === sequence.current)
        setError(e instanceof Error ? e.message : "Trợ lý chưa sẵn sàng.");
    } finally {
      if (active.current && ticket === sequence.current) setBusy(false);
    }
  };

  return (
    <div className="fixed bottom-20 right-6 z-50 flex flex-col items-end">
      {open && (
        <section
          aria-label="Trợ lý phác đồ"
          className="mb-3 flex h-[480px] w-[min(420px,90vw)] flex-col overflow-hidden rounded-xl border bg-white shadow-2xl"
        >
          <header className="flex items-center justify-between bg-purple-950 p-4 text-white">
            <h2 className="text-sm font-semibold">Tra cứu hướng dẫn có nguồn</h2>
            <button onClick={() => setOpen(false)} aria-label="Đóng trợ lý">
              ✕
            </button>
          </header>
          <div className="flex-1 space-y-4 overflow-y-auto p-4">
            {!consultationId ? (
              <p className="text-sm">
                Mở một{" "}
                <Link href="/dashboard/queue" className="text-purple-700 underline">
                  ca khám được phân công
                </Link>{" "}
                để trợ lý lấy đúng ngữ cảnh.
              </p>
            ) : (
              <>
                <p className="text-xs text-slate-500">
                  Tra cứu local từ nguồn đã duyệt. Hệ thống không tự kê toa/chỉ định và không đọc nội dung màn hình.
                </p>
                {messages.map((m, i) => (
                  <div key={m.response?.interactionId || i}>
                    <p className="rounded-lg bg-purple-50 p-2 text-sm">{m.question}</p>
                    <div className="mt-2 text-sm">
                      <FormattedAiText content={m.response?.summary || ""} />
                    </div>
                    {m.response && <ClinicalCitations response={m.response} />}
                    {m.response?.missingInformation?.map((v, k) => (
                      <p key={k} className="mt-1 text-xs text-amber-800">
                        {v}
                      </p>
                    ))}
                  </div>
                ))}
              </>
            )}
            {error && (
              <p role="alert" className="text-sm text-red-700">
                {error}
              </p>
            )}
            {busy && (
              <p role="status" className="text-sm">
                Đang kiểm tra ngữ cảnh và nguồn…
              </p>
            )}
          </div>
          <form onSubmit={send} className="flex gap-2 border-t p-3">
            <input
              aria-label="Câu hỏi trợ lý"
              maxLength={2000}
              disabled={!consultationId || busy}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Hỏi về hướng dẫn đã duyệt…"
              className="min-w-0 flex-1 rounded-lg border p-2 text-sm"
            />
            <button
              disabled={!consultationId || busy || !text.trim()}
              className="rounded-lg bg-purple-700 px-3 text-sm text-white disabled:opacity-50"
            >
              Gửi
            </button>
          </form>
        </section>
      )}
      <button
        onClick={() => setOpen((v) => !v)}
        aria-label="Mở trợ lý phác đồ"
        className="flex h-14 w-14 items-center justify-center rounded-full border bg-white shadow-lg"
      >
        <Image src="/ai-ferret-cutout.png" width={46} height={46} alt="CareFlow AI" />
      </button>
    </div>
  );
}
