"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { getDefaultRouteForRole } from "@/lib/role-utils";
import LoadingSpinner from "@/components/ui/LoadingSpinner";

export default function DashboardIndexPage() {
  const router = useRouter();
  const { user, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading) {
      const target = getDefaultRouteForRole(user?.role);
      router.replace(target);
    }
  }, [user?.role, isLoading, router]);

  return (
    <div className="flex h-64 items-center justify-center">
      <LoadingSpinner size="md" />
    </div>
  );
}
