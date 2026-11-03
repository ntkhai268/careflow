import type { ReactNode } from "react";

export default function HospitalAuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-[#F5F8FA]">
      {/* Left column — CareFlow Ocean Medical Banner (Synchronized with Patient Mobile) */}
      <div
        data-auth-banner
        className="hidden lg:flex lg:w-[50vw] lg:sticky lg:top-0 lg:h-screen lg:self-start lg:shrink-0 flex-col justify-between bg-gradient-to-br from-[#0277A8] via-[#00577E] to-[#0B2534] p-12 text-white relative overflow-hidden"
      >
        {/* Subtle decoration grid */}
        <div className="absolute inset-0 bg-[linear-gradient(to_right,#ffffff0a_1px,transparent_1px),linear-gradient(to_bottom,#ffffff0a_1px,transparent_1px)] bg-[size:24px_24px]" />

        {/* Ambient glow */}
        <div className="absolute -bottom-24 -right-24 w-96 h-96 rounded-full bg-[#087F6A]/30 blur-3xl pointer-events-none" />

        <div>
          <div className="flex items-center gap-3.5 relative z-10">
            {/* Modular Cross Logo Icon (SVG) */}
            <svg width="40" height="40" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="35" y="0" width="30" height="30" rx="6" fill="white" fillOpacity="0.2" />
              <rect x="0" y="35" width="30" height="30" rx="6" fill="white" fillOpacity="0.2" />
              <rect x="35" y="35" width="30" height="30" rx="6" fill="#8BD2EE" />
              <rect x="70" y="35" width="30" height="30" rx="6" fill="white" fillOpacity="0.2" />
              <rect x="35" y="70" width="30" height="30" rx="6" fill="white" fillOpacity="0.2" />
            </svg>
            <div>
              <span className="text-2xl font-bold tracking-tight block leading-none">
                Cổng thông tin CareFlow
              </span>
              <span className="text-xs text-[#8BD2EE] font-medium tracking-wide">
                Đồng bộ Hành trình Khám chữa bệnh Thông minh
              </span>
            </div>
          </div>
        </div>

        <div className="space-y-6 relative z-10 max-w-lg">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 border border-white/15 text-xs font-semibold text-[#DDF3FC]">
            <span className="w-2 h-2 rounded-full bg-[#8BD2EE]" />
            <span>Hệ sinh thái Y tế Số CareFlow</span>
          </div>
          <h2 className="text-5xl font-extrabold leading-tight tracking-tight">
            Chúng tôi điều trị cho bạn.<br />Chúng tôi chăm sóc bạn.
          </h2>
          <p className="text-base text-white/85 leading-relaxed font-medium">
            Hệ thống quản lý lâm sàng, điều phối hàng đợi thời gian thực và kê đơn thuốc điện tử liên thông trực tiếp với ứng dụng bệnh nhân.
          </p>
        </div>

        <p className="text-xs text-white/60 relative z-10">
          Hệ thống Lâm sàng CareFlow · Chuẩn thiết kế Y tế Đồng nhất Web &amp; Mobile
        </p>
      </div>
      <div className="flex w-full items-center justify-center px-6 py-12 lg:w-1/2 lg:min-w-0 bg-[#F5F8FA]">
        <div className="w-full max-w-md bg-white border border-[#D5E0E6] rounded-2xl p-8 shadow-[0_8px_24px_rgba(23,36,45,0.06)] space-y-6">
          {children}
        </div>
      </div>
    </div>
  );
}

export function HospitalAuthBrand() {
  return (
    <>
      {/* Modular Cross Logo Icon */}
      <div className="mb-3">
        <svg width="56" height="56" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="35" y="0" width="30" height="30" rx="6" fill="#0277A8" />
          <rect x="0" y="35" width="30" height="30" rx="6" fill="#0277A8" />
          <rect x="35" y="35" width="30" height="30" rx="6" fill="#087F6A" />
          <rect x="70" y="35" width="30" height="30" rx="6" fill="#0277A8" />
          <rect x="35" y="70" width="30" height="30" rx="6" fill="#0277A8" />
        </svg>
      </div>

      {/* careflow logo text + tiny connector graph */}
      <div className="flex flex-col items-center lg:items-start">
        <div className="flex items-end gap-1.5">
          <h1 className="text-3xl font-extrabold tracking-tight leading-none">
            <span className="text-[#0277A8]">care</span>
            <span className="text-[#087F6A]">flow</span>
          </h1>

          {/* tiny square connector graph */}
          <svg
            width="24"
            height="12"
            viewBox="0 0 48 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            className="mb-1"
          >
            <path d="M 6 18 L 18 10 L 30 18 L 42 10" stroke="#0277A8" strokeWidth="3" />
            <rect x="2" y="14" width="8" height="8" rx="2" fill="#0277A8" />
            <rect x="14" y="6" width="8" height="8" rx="2" fill="#0277A8" />
            <rect x="26" y="14" width="8" height="8" rx="2" fill="#087F6A" />
            <rect x="38" y="6" width="8" height="8" rx="2" fill="#087F6A" />
          </svg>
        </div>
        <p className="text-[10px] uppercase tracking-widest font-bold text-[#667985] mt-1.5">
          Cổng Nghiệp Vụ Bác Sĩ &amp; Nhân Viên Y Tế
        </p>
      </div>
    </>
  );
}
