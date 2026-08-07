"use client";

export default function Footer() {
  return (
    <footer className="w-full bg-primary-600 text-white mt-auto">
      {/* Cookie banner / Informational strip */}
      <div className="w-full bg-[#561A66] px-6 py-4 flex flex-col md:flex-row justify-between items-center gap-4 text-sm font-medium border-b border-white/10">
        <p className="text-white/90 text-center md:text-left leading-relaxed">
          Chúng tôi sử dụng cookie của riêng mình và bên thứ ba để cải thiện dịch vụ của chúng tôi và hiển thị quảng cáo liên quan đến sở thích của bạn bằng cách phân tích thói quen duyệt web. Nếu bạn tiếp tục duyệt, chúng tôi coi như bạn đồng ý với việc sử dụng cookie. Bạn có thể thay đổi cấu hình hoặc xem thêm thông tin tại <span className="underline hover:text-white cursor-pointer">chính sách cookie</span> của chúng tôi.
        </p>
        <div className="flex gap-4 shrink-0">
          <button className="bg-white text-primary-600 px-6 py-2 font-bold hover:bg-gray-100 transition-colors">
            CHẤP NHẬN
          </button>
          <button className="border border-white text-white px-6 py-2 font-bold hover:bg-white/10 transition-colors">
            THÔNG TIN CHI TIẾT &gt;
          </button>
        </div>
      </div>

      {/* Main footer credentials */}
      <div className="w-full px-6 py-6 flex flex-col sm:flex-row justify-between items-center gap-4 text-xs text-white/70">
        <p>© 2026 CareFlow — Hợp tác cùng Sant Joan de Déu Barcelona. Bảo lưu mọi quyền.</p>
        <div className="flex gap-6">
          <span className="hover:text-white cursor-pointer">Thông tin pháp lý</span>
          <span className="hover:text-white cursor-pointer">Chính sách bảo mật</span>
          <span className="hover:text-white cursor-pointer">Khả năng tiếp cận</span>
        </div>
      </div>
    </footer>
  );
}
