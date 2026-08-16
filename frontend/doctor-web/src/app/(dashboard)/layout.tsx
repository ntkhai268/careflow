"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import Sidebar from "@/components/layout/Sidebar";
import Topbar from "@/components/layout/Topbar";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import AiAssistantWidget from "@/components/AiAssistantWidget";
import AccessDenied from "@/components/AccessDenied";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isAuthenticated, isLoading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [isCollapsed, setIsCollapsed] = useState(false);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isAuthenticated, isLoading, router]);

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

  const userRole = user?.role?.toUpperCase() || "";
  const isAdmin = userRole.includes("ADMIN");
  const isLabTech = userRole.includes("LAB");
  const isStaff = userRole.includes("STAFF");
  const isDoctor = userRole.includes("DOCTOR");

  let isAuthorized = true;
  let requiredRoleLabel = "";

  if (pathname.startsWith("/admin") && !isAdmin) {
    isAuthorized = false;
    requiredRoleLabel = "Quản trị viên (ADMIN)";
  } else if (pathname.startsWith("/lab") && !isLabTech) {
    isAuthorized = false;
    requiredRoleLabel = "Kỹ thuật viên Cận lâm sàng (LAB_TECHNICIAN)";
  } else if (pathname.startsWith("/staff") && !isStaff) {
    isAuthorized = false;
    requiredRoleLabel = "Nhân viên Tiếp nhận & Dược (STAFF)";
  } else if ((pathname.startsWith("/dashboard") || pathname.startsWith("/consultation")) && !isDoctor) {
    isAuthorized = false;
    requiredRoleLabel = "Bác sĩ khám bệnh (DOCTOR)";
  }

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
        <main className="flex-1 overflow-y-auto p-6 pb-24 relative z-10">
          {isAuthorized ? (
            children
          ) : (
            <AccessDenied requiredRoleLabel={requiredRoleLabel} />
          )}
        </main>

        {/* Floating AI Assistant Widget */}
        <AiAssistantWidget />
      </div>
    </div>
  );
}
