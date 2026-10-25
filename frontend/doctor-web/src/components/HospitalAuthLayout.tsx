import type { ReactNode } from "react";

export default function HospitalAuthLayout({children}: {children: ReactNode}) {
  return (
    <div className="flex min-h-screen">
      {/* Left column — SJD Hospital Banner (Visible on Desktop only) */}
      <div data-auth-banner className="hidden lg:flex lg:w-[50vw] lg:sticky lg:top-0 lg:h-screen lg:self-start lg:shrink-0 flex-col justify-between bg-gradient-to-br from-[#6E2582] via-[#561A66] to-[#2B1D30] p-12 text-white relative overflow-hidden">
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
      <div className="flex w-full items-center justify-center px-6 py-12 lg:w-1/2 lg:min-w-0 bg-background">
        <div className="w-full max-w-md space-y-8">{children}</div>
      </div>
    </div>
  );
}

export function HospitalAuthBrand() {
  return <>
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
  </>;
}
