"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import Sidebar from "@/components/layout/Sidebar";
import Topbar from "@/components/layout/Topbar";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import AiAssistantWidget from "@/components/AiAssistantWidget";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();
  const [isCollapsed, setIsCollapsed] = useState(false);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isAuthenticated, isLoading, router]);

  // Global Keyboard Shortcuts for Tab Switching
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.altKey || e.ctrlKey) {
        if (e.key === "1") {
          e.preventDefault();
          router.push("/dashboard/general");
        } else if (e.key === "2") {
          e.preventDefault();
          router.push("/dashboard/queue");
        } else if (e.key === "3") {
          e.preventDefault();
          router.push("/dashboard/prescriptions");
        }
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [router]);

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#F1F5F9]">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (!isAuthenticated) return null;

  return (
    <div className="flex h-screen bg-[#F1F5F9]">
      <Sidebar
        isCollapsed={isCollapsed}
        onToggleCollapse={() => setIsCollapsed(!isCollapsed)}
      />
      <div
        className={`flex flex-1 flex-col overflow-hidden relative transition-all duration-300 ease-in-out ${
          isCollapsed ? "ml-16" : "ml-64"
        }`}
      >
        {/* Subtle decorative grid — light cyan tinted lines */}
        <div className="absolute inset-0 bg-[linear-gradient(to_right,#6366F108_1px,transparent_1px),linear-gradient(to_bottom,#6366F108_1px,transparent_1px)] bg-[size:24px_24px] pointer-events-none" />

        <Topbar />
        <main className="flex-1 overflow-y-auto p-6 pb-24 relative z-10">{children}</main>

        {/* Floating AI Assistant Widget */}
        <AiAssistantWidget />
      </div>
    </div>
  );
}
