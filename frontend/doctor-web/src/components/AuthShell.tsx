import Link from "next/link";
import type { ReactNode } from "react";
import HospitalAuthLayout, { HospitalAuthBrand } from "./HospitalAuthLayout";

export function AuthIcon({name, className = "h-5 w-5"}: {name: "passkey" | "question" | "lock" | "arrow" | "back" | "check" | "shield" | "mail"; className?: string}) {
  const paths = {
    passkey: <><circle cx="8" cy="7" r="3"/><path d="M2 20v-3a6 6 0 0 1 11-3M19 14v7m0-3h3"/><circle cx="19" cy="10" r="4"/></>,
    question: <><path d="M20 11.5a8 8 0 0 1-8 8H5l-4 3v-11a9 9 0 1 1 19 0Z"/><path d="M8 8a3 3 0 0 1 6 0c0 2-3 2-3 4m0 3h.01"/></>,
    lock: <><rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/></>,
    arrow: <path d="m9 5 7 7-7 7"/>,
    back: <path d="m12 5-7 7 7 7M5 12h15"/>,
    check: <path d="m5 12 4 4L19 6"/>,
    shield: <><path d="M12 3 3 7v5c0 5 9 10 9 10s9-5 9-10V7l-9-4Z"/><path d="m8 12 3 3 5-6"/></>,
    mail: <><rect x="3" y="5" width="18" height="14" rx="3"/><path d="m3 7 9 6 9-6"/></>,
  };
  return <svg aria-hidden="true" className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>;
}

export default function AuthShell({children, backHref, onBack, backLabel = "Quay lại đăng nhập"}: {children: ReactNode; backHref?: string; onBack?: () => void; backLabel?: string}) {
  return <HospitalAuthLayout>
    <div className="flex flex-col items-center text-center lg:items-start lg:text-left">
      <HospitalAuthBrand />
    </div>
    <section className="text-foreground">
      {(backHref || onBack) && (onBack ? <button type="button" onClick={onBack} className="mb-5 inline-flex min-h-10 items-center gap-2 rounded-lg text-sm font-medium text-gray-500 hover:text-[#6E2582] focus-visible:outline-2 focus-visible:outline-[#6E2582]"><AuthIcon name="back" className="h-4 w-4"/>{backLabel}</button> : <Link href={backHref!} className="mb-5 inline-flex min-h-10 items-center gap-2 rounded-lg text-sm font-medium text-gray-500 hover:text-[#6E2582] focus-visible:outline-2 focus-visible:outline-[#6E2582]"><AuthIcon name="back" className="h-4 w-4"/>{backLabel}</Link>)}
      {children}
    </section>
  </HospitalAuthLayout>;
}

export const authInput = "mt-2 min-h-12 w-full rounded-lg border border-input-border bg-input-bg px-4 py-2.5 text-sm text-foreground placeholder:text-gray-400 focus:border-input-focus focus:outline-none focus:ring-1 focus:ring-input-focus transition-all disabled:opacity-60";
export const authPrimary = "flex min-h-12 w-full items-center justify-center gap-2 bg-primary-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-primary-700 transition-all focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-input-focus active:scale-[0.99] disabled:cursor-wait disabled:opacity-50";
