"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import HospitalAuthLayout, { HospitalAuthBrand } from "@/components/HospitalAuthLayout";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { getDefaultRouteForRole } from "@/lib/role-utils";

export default function LoginPage() {
  const { user, login, isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState<{ message: string; type: "danger" | "warning" } | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      const target = getDefaultRouteForRole(user?.role);
      router.replace(target);
    }
  }, [isAuthenticated, isLoading, user?.role, router]);

  if (isAuthenticated) {
    return null;
  }

  const handleValidateAndSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const identifier = email.trim();
    if (!identifier) {
      setError({ message: "Vui lòng nhập email hoặc tên đăng nhập.", type: "danger" });
      return;
    }

    setIsSubmitting(true);

    try {
      await login(identifier, password);
      setPassword("");
    } catch (err) {
      setError({
        message: err instanceof Error ? err.message : "Email hoặc mật khẩu không chính xác.",
        type: "danger"
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <HospitalAuthLayout>
      {/* Header info inside the form */}
      <div className="text-center lg:text-left flex flex-col items-center lg:items-start">
        <HospitalAuthBrand />

        <h2 className="text-xl font-bold text-foreground mt-8">ĐĂNG NHẬP HỆ THỐNG</h2>
        <p className="mt-1 text-sm text-gray-500">
          Sử dụng tài khoản Bác sĩ được cấp để truy cập cổng nghiệp vụ
        </p>
      </div>

      {/* Error notifications using design.md semantic colors */}
      {error && (
        <div
          className={`border p-3 text-sm flex gap-3 items-start ${
            error.type === "danger"
              ? "border-danger/30 bg-danger-light text-danger"
              : "border-warning/30 bg-warning-light text-warning"
          }`}
        >
          <svg className="w-5 h-5 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <span>{error.message}</span>
        </div>
      )}

      <form onSubmit={handleValidateAndSubmit} className="space-y-5">
        {/* Email or username input */}
        <div>
          <label htmlFor="email" className="mb-1.5 block text-sm font-semibold text-foreground">
            Email hoặc tên đăng nhập
          </label>
          <input
            id="email"
            type="text"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="staff hoặc an@careflow.vn"
            required
            className="w-full border border-input-border bg-input-bg px-4 py-2.5 text-sm text-foreground placeholder:text-gray-400 focus:border-input-focus focus:outline-none focus:ring-1 focus:ring-input-focus transition-all"
          />
        </div>

        {/* Password Input with show/hide toggle */}
        <div>
          <label htmlFor="password" className="mb-1.5 block text-sm font-semibold text-foreground">
            Mật khẩu
          </label>
          <div className="relative">
            <input
              id="password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              className="w-full border border-input-border bg-input-bg pl-4 pr-12 py-2.5 text-sm text-foreground placeholder:text-gray-400 focus:border-input-focus focus:outline-none focus:ring-1 focus:ring-input-focus transition-all"
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 focus:outline-none"
              title={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            >
              {showPassword ? (
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.5">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0012 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L17.772 17.772m0 0a5.25 5.25 0 11-7.544-7.544" />
                </svg>
              ) : (
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="1.5">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              )}
            </button>
          </div>
        </div>


        {/* Checkbox Remember Me & Forgot Password link */}
        <div className="flex items-center justify-between text-sm">
          <label className="flex items-center gap-2 text-gray-600 font-medium cursor-pointer">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="h-4 w-4 text-primary-600 border-gray-300 focus:ring-primary-500"
            />
            Ghi nhớ đăng nhập
          </label>
        </div>

        {/* Submit button */}
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full bg-primary-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-primary-700 transition-all focus:outline-none active:scale-[0.99] disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isSubmitting ? "Đang kết nối..." : "ĐĂNG NHẬP"}
        </button>
      </form>

      <nav aria-label="Hỗ trợ đăng nhập" className="space-y-1 border-t border-gray-200/80 pt-3">
        <Link href="/login/methods" className="group flex min-h-10 items-center gap-2.5 rounded-lg px-2 text-[13px] font-medium text-gray-500 transition-colors hover:bg-[#F3E8F5]/60 hover:text-[#6E2582] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#6E2582]">
          <svg aria-hidden="true" className="h-4 w-4 shrink-0 text-[#6E2582]/75" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><circle cx="8" cy="7" r="3"/><path d="M2 20v-3a6 6 0 0 1 11-3M19 14v7m0-3h3"/><circle cx="19" cy="10" r="4"/></svg>
          <span className="flex-1">Đổi hình thức đăng nhập</span>
          <svg aria-hidden="true" className="h-3.5 w-3.5 text-gray-400 group-hover:text-[#6E2582]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="m9 5 7 7-7 7"/></svg>
        </Link>
        <Link href="/forgot-password" className="group flex min-h-10 items-center gap-2.5 rounded-lg px-2 text-[13px] font-medium text-gray-500 transition-colors hover:bg-[#F3E8F5]/60 hover:text-[#6E2582] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#6E2582]">
          <svg aria-hidden="true" className="h-4 w-4 shrink-0 text-[#6E2582]/75" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/></svg>
          <span className="flex-1">Quên mật khẩu</span>
          <svg aria-hidden="true" className="h-3.5 w-3.5 text-gray-400 group-hover:text-[#6E2582]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="m9 5 7 7-7 7"/></svg>
        </Link>
      </nav>

    </HospitalAuthLayout>
  );
}
