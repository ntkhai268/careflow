import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/contexts/AuthContext";
import InteractiveSession from "@/components/InteractiveSession";
import ReactGrepInspector from "@/components/ReactGrepInspector";

const inter = Inter({
  variable: "--font-geist-sans",
  subsets: ["latin", "vietnamese"],
});

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "CareFlow — Doctor Portal",
  description: "Hệ thống quản lý khám chữa bệnh dành cho bác sỹ",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className={`${inter.variable} h-full antialiased`} suppressHydrationWarning>
      <body className="min-h-full flex flex-col font-sans" suppressHydrationWarning>
        <ReactGrepInspector />
        <AuthProvider><InteractiveSession />{children}</AuthProvider>
      </body>
    </html>
  );
}
