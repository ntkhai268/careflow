"use client";

import React, { useState, useEffect, useRef } from "react";
import { useRouter, usePathname } from "next/navigation";
import { aiApi } from "@/lib/ai-api";
import { renderFormattedAiText } from "@/lib/format-ai-text";

interface ChatMessage {
  id: string;
  sender: "ai" | "doctor";
  text: string;
  timestamp: string;
  consultationId?: string;
}

export default function AiAssistantWidget() {
  const router = useRouter();
  const pathname = usePathname();
  const isConsultationPage = pathname?.startsWith("/consultation/");
  const [isOpen, setIsOpen] = useState(false);
  const [showSpeechBubble, setShowSpeechBubble] = useState(false); // Default false - no random bubble
  const [isFadingOut, setIsFadingOut] = useState(false);
  const [speechBubbleText, setSpeechBubbleText] = useState("");
  const [speechConsultationId, setSpeechConsultationId] = useState<string | undefined>(undefined);
  const [inputText, setInputText] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  
  const [isTyping, setIsTyping] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "1",
      sender: "ai",
      text: "Xin chào Bác sĩ! Em là Bác sĩ Chồn AI (CareFlow Assistant) 🐾. Em có thể hỗ trợ Bác sĩ tra cứu thông tin dược lý, tương tác thuốc hay tóm tắt chỉ số sinh hiệu ạ!",
      timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })
    }
  ]);

  // Listen to custom window events for AI Proactive Notifications ONLY
  useEffect(() => {
    function handleAiNotify(e: Event) {
      const customEvt = e as CustomEvent<{ text: string; consultationId?: string }>;
      if (!customEvt.detail) return;
      const { text, consultationId } = customEvt.detail;

      if (isOpen) {
        // Chat box is OPEN -> Push notification message directly into chat box!
        setMessages(prev => [
          ...prev,
          {
            id: Date.now().toString(),
            sender: "ai",
            text,
            timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }),
            consultationId
          }
        ]);
      } else {
        // Chat box is CLOSED -> Show speech bubble ONLY for real notification events!
        setSpeechBubbleText(text);
        setSpeechConsultationId(consultationId);
        setShowSpeechBubble(true);
        setIsFadingOut(false);
      }
    }

    window.addEventListener("careflow:ai-notify", handleAiNotify);
    return () => window.removeEventListener("careflow:ai-notify", handleAiNotify);
  }, [isOpen]);

  // Auto scroll to bottom when new chat messages arrive or typing status changes
  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, isOpen, isTyping]);

  // Auto fade-out notification speech bubble after 8s
  useEffect(() => {
    if (!showSpeechBubble) return;

    const fadeTimer = setTimeout(() => {
      setIsFadingOut(true);
    }, 7700);

    const hideTimer = setTimeout(() => {
      setShowSpeechBubble(false);
      setIsFadingOut(false);
    }, 8000);

    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(hideTimer);
    };
  }, [showSpeechBubble, speechBubbleText]);

  // Dynamically capture live screen context for AI
  const getScreenContext = () => {
    const pageRoute = pathname || "/";
    let pageTitle = "Hệ thống Quản lý Bệnh viện CareFlow";

    if (pageRoute.startsWith("/consultation/")) {
      pageTitle = "Trang Khám bệnh Chi tiết Bác sĩ";
    } else if (pageRoute.includes("/dashboard/queue")) {
      pageTitle = "Trang Hàng chờ Khám bệnh";
    } else if (pageRoute.includes("/lab/queue")) {
      pageTitle = "Trang Hàng chờ Xét nghiệm Cận lâm sàng (Lab Queue)";
    } else if (pageRoute.includes("/staff/pharmacy")) {
      pageTitle = "Trang Quầy phát thuốc";
    } else if (pageRoute.includes("/dashboard/general")) {
      pageTitle = "Trang Tổng quan Dashboard Bác sĩ";
    }

    let pageDataSnippet = "";
    if (typeof document !== "undefined") {
      const mainElement = document.querySelector("main") || document.body;
      if (mainElement) {
        pageDataSnippet = mainElement.innerText.replace(/\s+/g, " ").slice(0, 1500);
      }
    }

    return { pageRoute, pageTitle, pageData: pageDataSnippet };
  };

  const handleSendMessage = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputText.trim() || isTyping) return;

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      sender: "doctor",
      text: inputText,
      timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })
    };

    setMessages(prev => [...prev, userMsg]);
    const currentQuery = inputText;
    setInputText("");
    setIsTyping(true);

    try {
      const activeConsultationId = speechConsultationId || "DEMO-CONSULTATION-01";
      const screenContext = getScreenContext();
      const res = await aiApi.sendClinicalChat(activeConsultationId, currentQuery, screenContext);
      
      const aiReplyText = res.data?.summary || `Bác sĩ Chồn AI 🐾: Đã ghi nhận câu hỏi "${currentQuery}".`;
      
      const aiReply: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: "ai",
        text: aiReplyText,
        timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })
      };
      setMessages(prev => [...prev, aiReply]);
    } catch {
      const fallbackReply: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: "ai",
        text: `Bác sĩ Chồn AI 🐾: Xin lỗi Bác sĩ, không thể kết nối tới AI Service. Vui lòng kiểm tra lại dịch vụ careflow-ai-service.`,
        timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })
      };
      setMessages(prev => [...prev, fallbackReply]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <div className={`fixed right-6 z-50 flex flex-col items-end pointer-events-none transition-all duration-300 ease-in-out ${
      isConsultationPage ? "bottom-20" : "bottom-5"
    }`}>
      {/* Popover Chat Window — Morph / Expand smoothly from bottom-right anchor */}
      {isOpen && (
        <div className="mb-3 w-80 sm:w-96 bg-white border border-slate-200 shadow-2xl rounded-none flex flex-col h-[440px] origin-bottom-right animate-in fade-in zoom-in-95 duration-200 ease-out pointer-events-auto">
          {/* Header */}
          <div className="p-3.5 bg-[#0D0F1E] text-white flex justify-between items-center border-b border-[#1E2340]">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-full overflow-hidden border border-indigo-400/50 bg-[#161930] flex items-center justify-center flex-shrink-0">
                <img
                  src="/ai-ferret-cutout.png"
                  alt="Bác sĩ Chồn AI"
                  className="w-10 h-10 object-contain translate-y-1 scale-125 animate-clinical-breathing"
                />
              </div>
              <div>
                <h3 className="text-xs font-bold tracking-wide flex items-center gap-1.5">
                  Bác sĩ Chồn AI 🐾
                  <span className="text-[9px] bg-indigo-900/90 text-indigo-200 px-1.5 py-0.5 rounded-none border border-indigo-500/30 font-medium">CareFlow AI</span>
                </h3>
                <span className="text-[10px] text-emerald-400 font-medium flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 inline-block animate-pulse"></span>
                  Sẵn sàng hỗ trợ Bác sĩ
                </span>
              </div>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="text-white/60 hover:text-white text-sm font-bold px-2 py-0.5 transition-colors"
              title="Đóng cửa sổ chat"
            >
              ✕
            </button>
          </div>

          {/* Chat Messages Body */}
          <div className="flex-1 overflow-y-auto p-3 space-y-3 bg-[#F8FAFC] custom-scrollbar">
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`flex gap-2 ${msg.sender === "doctor" ? "flex-row-reverse" : "flex-row"}`}
              >
                {msg.sender === "ai" && (
                  <div className="w-7 h-7 rounded-full overflow-hidden bg-indigo-950 border border-indigo-300 flex-shrink-0 flex items-center justify-center">
                    <img
                      src="/ai-ferret-cutout.png"
                      alt="Ferret AI"
                      className="w-9 h-9 object-contain translate-y-1 scale-125"
                    />
                  </div>
                )}
                <div className={`flex flex-col ${msg.sender === "doctor" ? "items-end" : "items-start"}`}>
                  <div
                    className={`max-w-[85%] p-2.5 text-xs leading-relaxed rounded-none shadow-xs ${
                      msg.sender === "doctor"
                        ? "bg-[#6366F1] text-white"
                        : "bg-white text-slate-800 border border-slate-200"
                    }`}
                  >
                    <div>{renderFormattedAiText(msg.text)}</div>
                    {msg.consultationId && (
                      <button
                        onClick={() => {
                          setIsOpen(false);
                          router.push(`/consultation/${msg.consultationId}`);
                        }}
                        className="mt-2 inline-flex items-center gap-1 bg-[#6366F1] hover:bg-indigo-700 text-white px-2.5 py-1 text-[10px] font-bold transition-colors rounded-none cursor-pointer"
                      >
                        <span>Đến ca khám hiện tại</span>
                        <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                        </svg>
                      </button>
                    )}
                  </div>
                  <span className="text-[9px] text-slate-400 mt-1 px-1">{msg.timestamp}</span>
                </div>
              </div>
            ))}

            {isTyping && (
              <div className="flex gap-2 flex-row animate-in fade-in duration-200">
                <div className="w-7 h-7 rounded-full overflow-hidden bg-indigo-950 border border-indigo-300 flex-shrink-0 flex items-center justify-center">
                  <img
                    src="/ai-ferret-cutout.png"
                    alt="Ferret AI"
                    className="w-9 h-9 object-contain translate-y-1 scale-125 animate-clinical-breathing"
                  />
                </div>
                <div className="bg-white text-slate-800 border border-slate-200 p-2.5 rounded-none shadow-xs flex items-center gap-2">
                  <span className="text-[11px] font-semibold text-indigo-700">CareFlow AI đang phân tích</span>
                  <span className="flex items-center gap-1">
                    <span className="w-1.5 h-1.5 bg-indigo-600 rounded-full animate-bounce [animation-delay:-0.3s]" />
                    <span className="w-1.5 h-1.5 bg-indigo-600 rounded-full animate-bounce [animation-delay:-0.15s]" />
                    <span className="w-1.5 h-1.5 bg-indigo-600 rounded-full animate-bounce" />
                  </span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick suggestions pills */}
          <div className="px-3 py-1.5 bg-white border-t border-slate-100 flex gap-1.5 overflow-x-auto text-[10px] whitespace-nowrap custom-scrollbar">
            {["Tra cứu Omeprazole", "Kiểm tra tương tác thuốc", "Tóm tắt sinh hiệu"].map((sug, i) => (
              <button
                key={i}
                type="button"
                onClick={() => setInputText(sug)}
                className="px-2 py-1 bg-indigo-50 hover:bg-indigo-100 text-[#6366F1] font-medium rounded-none transition-colors"
              >
                {sug}
              </button>
            ))}
          </div>

          {/* Input Footer */}
          <form onSubmit={handleSendMessage} className="p-2.5 bg-white border-t border-slate-200 flex gap-2">
            <input
              type="text"
              placeholder="Hỏi Bác sĩ Chồn AI..."
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              className="flex-1 px-3 py-1.5 text-xs border border-slate-300 focus:outline-none focus:border-[#6366F1] rounded-none text-slate-800 placeholder-slate-400"
            />
            <button
              type="submit"
              disabled={!inputText.trim()}
              className="bg-[#6366F1] hover:bg-indigo-600 disabled:opacity-40 text-white font-bold px-3 py-1.5 text-xs transition-colors rounded-none"
            >
              Gửi
            </button>
          </form>
        </div>
      )}

      {/* Stationary Clinical AI Trigger Button & Notification Speech Bubble */}
      <div className="relative flex items-center justify-end pointer-events-auto group">
        {/* Proactive Speech Bubble Notification (Shown ONLY on actual event notification) */}
        {showSpeechBubble && !isOpen && speechBubbleText && (
          <div className={`mr-3 bg-[#0D0F1E] text-white text-xs font-medium px-3.5 py-2.5 border border-[#1E2340] shadow-xl flex flex-col gap-1.5 max-w-[280px] rounded-2xl relative transition-opacity duration-300 ${
            isFadingOut ? "opacity-0" : "opacity-100 animate-in fade-in slide-in-from-right-3 duration-250"
          }`}>
            <div className="absolute right-[-6px] top-1/2 -translate-y-1/2 w-0 h-0 border-t-[5px] border-t-transparent border-l-[6px] border-l-[#0D0F1E] border-b-[5px] border-b-transparent"></div>
            <div className="flex items-start justify-between gap-2">
              <span className="text-xs leading-relaxed text-slate-200">{speechBubbleText}</span>
              <button
                onClick={(e) => { e.stopPropagation(); setShowSpeechBubble(false); }}
                className="text-slate-400 hover:text-white text-xs font-bold"
                title="Đóng thông báo"
              >
                ✕
              </button>
            </div>
            {speechConsultationId && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setShowSpeechBubble(false);
                  router.push(`/consultation/${speechConsultationId}`);
                }}
                className="text-[#818CF8] hover:text-white text-[11px] font-semibold underline flex items-center gap-1 mt-0.5 transition-colors cursor-pointer"
              >
                <span>Đến ca khám hiện tại</span>
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </button>
            )}
          </div>
        )}

        {/* Stationary AI Trigger Button (NO hover auto-show bubble!) */}
        <button
          onClick={() => {
            setIsOpen(!isOpen);
            setShowSpeechBubble(false);
          }}
          title="Bác sĩ Chồn AI CareFlow 🐾"
          className="relative w-14 h-14 rounded-full border-2 border-[#6366F1] hover:border-indigo-400 bg-transparent shadow-xl transition-transform duration-200 hover:scale-105 active:scale-95 flex items-center justify-center overflow-visible cursor-pointer focus:outline-none"
        >
          {/* Soft Glow Pulse Ring */}
          <span className="absolute inset-0 rounded-full border-2 border-[#6366F1] pointer-events-none animate-clinical-pulse-ring" />

          {/* Online Status Dot */}
          <span className="absolute top-0.5 right-0.5 z-10 w-2.5 h-2.5 bg-emerald-500 border-2 border-[#0D0F1E] rounded-full" />

          {/* Mascot Image with Subtle Breathing */}
          <div className="w-full h-full rounded-full overflow-hidden flex items-center justify-center">
            <img
              src="/ai-ferret-cutout.png"
              alt="Bác sĩ Chồn AI"
              className="w-16 h-16 object-contain scale-110 translate-y-1 animate-clinical-breathing"
            />
          </div>
        </button>
      </div>
    </div>
  );
}
