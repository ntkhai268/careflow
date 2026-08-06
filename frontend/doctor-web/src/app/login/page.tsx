"use client";

import { useState, useEffect } from "react";
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

    // Validate email format
    const emailRegex = /\S+@\S+\.\S+/;
    if (!emailRegex.test(email)) {
      setError({ message: "Định dạng email không hợp lệ (Ví dụ: bacsi@careflow.vn)", type: "danger" });
      return;
    }

    setIsSubmitting(true);

    try {
      // Simulate account state testing
      if (email === "locked@careflow.vn") {
        await new Promise((resolve) => setTimeout(resolve, 800));
        setError({
          message: "Tài khoản của Bác sĩ đã bị khóa do nhập sai nhiều lần. Vui lòng liên hệ quản trị viên hệ thống.",
          type: "warning"
        });
        return;
      }

      if (email === "expired@careflow.vn") {
        await new Promise((resolve) => setTimeout(resolve, 800));
        setError({
          message: "Mật khẩu của Bác sĩ đã hết hạn sử dụng theo chính sách bảo mật định kỳ. Vui lòng đổi mật khẩu mới.",
          type: "warning"
        });
        return;
      }

      // Normal login
      await login(email, password);
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
    <div className="flex min-h-screen">
      {/* Left column — SJD Hospital Banner (Visible on Desktop only) */}
      <div className="hidden lg:flex lg:w-1/2 flex-col justify-between bg-gradient-to-br from-[#6E2582] via-[#561A66] to-[#2B1D30] p-12 text-white relative overflow-hidden">
        {/* Subtle decoration grid */}
        <div className="absolute inset-0 bg-[linear-gradient(to_right,#ffffff05_1px,transparent_1px),linear-gradient(to_bottom,#ffffff05_1px,transparent_1px)] bg-[size:24px_24px]" />
        
        <div>
          <div className="flex items-center gap-3 relative z-10">
            {/* Modular Cross Logo Icon (SVG) */}
            <svg width="40" height="40" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="35" y="0" width="30" height="30" fill="white" fillOpacity="0.15" />
              <rect x="0" y="35" width="30" height="30" fill="white" fillOpacity="0.15" />
              <rect x="35" y="35" width="30" height="30" fill="#E05A17" />
              <rect x="70" y="35" width="30" height="30" fill="white" fillOpacity="0.15" />
              <rect x="35" y="70" width="30" height="30" fill="white" fillOpacity="0.15" />
            </svg>
            <span className="text-2xl font-bold tracking-tight">Cổng thông tin CareFlow</span>
          </div>
        </div>

        <div className="space-y-6 relative z-10 max-w-lg">
          <h2 className="text-5xl font-extrabold leading-tight tracking-tight">
            Chúng tôi điều trị cho bạn.<br />Chúng tôi chăm sóc bạn.
          </h2>
          <p className="text-lg text-white/80 leading-relaxed font-medium">
            Hệ thống quản lý lâm sàng, khám bệnh và kê đơn thuốc điện tử liên thông thông tin y tế.
          </p>
        </div>

        <p className="text-xs text-white/50 relative z-10">
          Hệ thống Lâm sàng CareFlow · Hợp tác cùng SJD Barcelona
        </p>
      </div>

      {/* Right column — Login form */}
      <div className="flex w-full items-center justify-center px-6 py-12 lg:w-1/2 bg-background">
        <div className="w-full max-w-md space-y-8">
          
          {/* Header info inside the form */}
          <div className="text-center lg:text-left flex flex-col items-center lg:items-start">
            {/* Modular Cross Logo Icon */}
            <div className="mb-4">
              <svg width="64" height="64" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="35" y="0" width="30" height="30" fill="#6E2582" />
                <rect x="0" y="35" width="30" height="30" fill="#6E2582" />
                <rect x="35" y="35" width="30" height="30" fill="#E05A17" />
                <rect x="70" y="35" width="30" height="30" fill="#6E2582" />
                <rect x="35" y="70" width="30" height="30" fill="#6E2582" />
              </svg>
            </div>

            {/* careflow logo text + tiny connector graph */}
            <div className="flex flex-col items-center lg:items-start">
              <div className="flex items-end gap-1">
                <h1 className="text-3xl font-extrabold tracking-tight leading-none">
                  <span className="text-accent">c</span>
                  <span className="text-[#2B1D30]">areflow</span>
                </h1>
                
                {/* tiny square connector graph */}
                <svg width="24" height="12" viewBox="0 0 48 24" fill="none" xmlns="http://www.w3.org/2000/svg" className="mb-1">
                  <path d="M 6 18 L 18 10 L 30 18 L 42 10" stroke="#6E2582" strokeWidth="3" />
                  <rect x="2" y="14" width="8" height="8" fill="#6E2582" />
                  <rect x="14" y="6" width="8" height="8" fill="#6E2582" />
                  <rect x="26" y="14" width="8" height="8" fill="#E05A17" />
                  <rect x="38" y="6" width="8" height="8" fill="#E05A17" />
                </svg>
              </div>
              <p className="text-[9px] uppercase tracking-widest font-bold text-gray-400 mt-1">
                Giải pháp CareFlow
              </p>
            </div>

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
            {/* Email Input */}
            <div>
              <label htmlFor="email" className="mb-1.5 block text-sm font-semibold text-foreground">
                Email nhân viên
              </label>
              <input
                id="email"
                type="text"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="an@careflow.vn"
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
              <a href="#" className="font-bold text-primary-600 hover:text-primary-800 transition-colors">
                Quên mật khẩu?
              </a>
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

        </div>
      </div>
    </div>
  );
}
