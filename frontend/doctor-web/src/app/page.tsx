"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { getDefaultRouteForRole } from "@/lib/role-utils";

export default function Home() {
  const { user, isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading) {
      if (isAuthenticated) {
        const target = getDefaultRouteForRole(user?.role);
        router.replace(target);
      } else {
        router.replace("/login");
      }
    }
  }, [isAuthenticated, isLoading, user?.role, router]);

  return (
    <div className="flex h-screen items-center justify-center bg-background">
      <div className="flex items-center gap-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
        <span className="text-sm text-gray-500">Đang tải...</span>
      </div>
    </div>
  );
}
