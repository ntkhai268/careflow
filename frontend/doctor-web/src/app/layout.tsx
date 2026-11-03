import type { Metadata } from "next";
import { Be_Vietnam_Pro, Roboto_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/contexts/AuthContext";
import InteractiveSession from "@/components/InteractiveSession";
import ReactGrepInspector from "@/components/ReactGrepInspector";

const beVietnamPro = Be_Vietnam_Pro({
  variable: "--font-geist-sans",
  weight: ["400", "500", "600", "700", "800"],
  subsets: ["latin", "vietnamese"],
  display: "swap",
});

const robotoMono = Roboto_Mono({
  variable: "--font-geist-mono",
  weight: ["400", "500", "600", "700"],
  subsets: ["latin", "vietnamese"],
  display: "swap",
});

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "CareFlow — Cổng Thông Tin Bác Sĩ & Nhân Viên Y Tế",
  description: "Hệ thống điều phối khám chữa bệnh lâm sàng CareFlow đồng bộ Đa nền tảng",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="vi"
      className={`${beVietnamPro.variable} ${robotoMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full flex flex-col font-sans text-[#17242D] bg-[#F5F8FA]" suppressHydrationWarning>
        <ReactGrepInspector />
        <AuthProvider><InteractiveSession />{children}</AuthProvider>
      </body>
    </html>
  );
}
