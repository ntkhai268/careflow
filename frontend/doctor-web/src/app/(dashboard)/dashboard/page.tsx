"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import LoadingSpinner from "@/components/ui/LoadingSpinner";

export default function DashboardIndexPage() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/dashboard/general");
  }, [router]);

  return (
    <div className="flex h-64 items-center justify-center">
      <LoadingSpinner size="md" />
    </div>
  );
}
