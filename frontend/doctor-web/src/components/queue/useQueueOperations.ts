"use client";

import { useEffect } from "react";

export function notifyAi(text: string, consultationId?: string) {
  if (typeof window !== "undefined") {
    window.dispatchEvent(
      new CustomEvent("careflow:ai-notify", {
        detail: consultationId ? { text, consultationId } : { text },
      })
    );
  }
}

export function useVisibilityPolling(
  enabled: boolean,
  onRefresh: () => void,
  intervalMs = 10000
) {
  useEffect(() => {
    if (!enabled) return;
    const refresh = () => {
      if (document.visibilityState === "visible") {
        onRefresh();
      }
    };
    const timer = window.setInterval(refresh, intervalMs);
    window.addEventListener("focus", refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
    };
  }, [enabled, onRefresh, intervalMs]);
}
