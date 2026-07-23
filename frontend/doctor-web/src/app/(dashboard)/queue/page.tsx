export default function QueuePage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Hàng đợi khám bệnh</h1>
        <p className="mt-1 text-sm text-gray-500">
          Danh sách bệnh nhân đang chờ khám — sẽ được xây dựng ở Giai đoạn 2
        </p>
      </div>
      <div className="flex items-center justify-center rounded-xl border-2 border-dashed border-card-border bg-card-bg p-16">
        <div className="text-center">
          <svg className="mx-auto h-12 w-12 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
          <p className="mt-3 text-sm font-medium text-gray-500">Trang hàng đợi sẽ được triển khai ở Giai đoạn 2</p>
          <p className="mt-1 text-xs text-gray-400">Bao gồm: danh sách chờ, gọi bệnh nhân, xem hồ sơ, mở phiên khám</p>
        </div>
      </div>
    </div>
  );
}
