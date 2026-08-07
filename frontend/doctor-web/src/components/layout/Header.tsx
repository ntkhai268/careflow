"use client";

import Link from "next/link";

export default function Header() {
  return (
    <header className="w-full bg-white border-b border-gray-200">
      {/* Top micro-bar */}
      <div className="w-full border-b border-gray-100 bg-gray-50 px-6 py-2 text-xs font-semibold tracking-wider text-gray-500 flex justify-between items-center">
        <div className="flex gap-6">
          <span className="hover:text-primary-600 cursor-pointer border-b-2 border-primary-600 pb-0.5">BỆNH VIỆN</span>
          <span className="hover:text-primary-600 cursor-pointer pb-0.5">NGHIÊN CỨU</span>
          <span className="hover:text-primary-600 cursor-pointer pb-0.5">ĐÀO TẠO</span>
        </div>
        <div className="flex gap-6">
          <span className="hover:text-primary-600 cursor-pointer">DÀNH CHO CHUYÊN GIA</span>
          <span className="hover:text-primary-600 cursor-pointer">BỆNH NHÂN QUỐC TẾ</span>
          <span className="hover:text-primary-600 cursor-pointer">GIỚI THIỆU</span>
        </div>
      </div>

      {/* Main header bar */}
      <div className="w-full px-6 py-4 flex justify-between items-center">
        {/* Logo and Hospital Branding */}
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-3xl font-extrabold tracking-tight text-primary-600">CareFlow</span>
            <div className="h-8 w-px bg-gray-200 hidden sm:block" />
            <div className="hidden sm:flex flex-col text-left">
              <span className="text-sm font-bold text-gray-800 leading-none">Sant Joan de Déu</span>
              <span className="text-[10px] text-gray-500 font-medium">Barcelona · Bệnh viện đối tác</span>
            </div>
          </div>
        </div>

        {/* Navigation Items (SJD Style) */}
        <nav className="hidden lg:flex gap-8 text-base font-bold text-primary-600">
          <Link href="#" className="hover:text-primary-800 transition-colors">Trẻ em</Link>
          <Link href="#" className="hover:text-primary-800 transition-colors">Phụ nữ</Link>
          <Link href="#" className="hover:text-primary-800 transition-colors">Chuyên gia y tế</Link>
          <Link href="#" className="text-gray-500 hover:text-primary-600 transition-colors">Chúng tôi chăm sóc bạn</Link>
        </nav>

        {/* Search / Action */}
        <div className="flex items-center gap-4">
          <button className="flex items-center gap-2 text-sm font-bold text-accent hover:text-accent-hover">
            <span>TÌM KIẾM</span>
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="2.5">
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </button>
        </div>
      </div>
    </header>
  );
}
