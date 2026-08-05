"use client";

import { useState } from "react";

interface AccountItem {
  id: string;
  fullName: string;
  email: string;
  role: "DOCTOR" | "LAB_TECHNICIAN" | "STAFF" | "ADMIN";
  department: string;
  status: "ACTIVE" | "LOCKED";
}

const INITIAL_ACCOUNTS: AccountItem[] = [
  { id: "1", fullName: "BS. Nguyễn Văn An", email: "an.nguyen@careflow.vn", role: "DOCTOR", department: "Khoa Nội", status: "ACTIVE" },
  { id: "2", fullName: "BS. Trần Thị Bình", email: "binh.tran@careflow.vn", role: "DOCTOR", department: "Khoa Nhi", status: "ACTIVE" },
  { id: "3", fullName: "KTV. Lê Hoàng Nam", email: "nam.le@careflow.vn", role: "LAB_TECHNICIAN", department: "Khoa Xét nghiệm", status: "ACTIVE" },
  { id: "4", fullName: "NV. Phạm Minh Thu", email: "thu.pham@careflow.vn", role: "STAFF", department: "Quầy Tiếp nhận", status: "ACTIVE" },
];

export default function AdminAccountsPage() {
  const [accounts, setAccounts] = useState<AccountItem[]>(INITIAL_ACCOUNTS);
  const [search, setSearch] = useState("");
  const [showAddModal, setShowAddModal] = useState(false);
  const [newFullName, setNewFullName] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newRole, setNewRole] = useState<AccountItem["role"]>("DOCTOR");
  const [newDept, setNewDept] = useState("Khoa Nội");

  const handleToggleStatus = (id: string) => {
    setAccounts(prev =>
      prev.map(acc =>
        acc.id === id ? { ...acc, status: acc.status === "ACTIVE" ? "LOCKED" : "ACTIVE" } : acc
      )
    );
  };

  const handleCreateAccount = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFullName.trim() || !newEmail.trim()) return;

    const newAcc: AccountItem = {
      id: String(Date.now()),
      fullName: newFullName.trim(),
      email: newEmail.trim(),
      role: newRole,
      department: newDept,
      status: "ACTIVE",
    };

    setAccounts(prev => [newAcc, ...prev]);
    setShowAddModal(false);
    setNewFullName("");
    setNewEmail("");
  };

  const filtered = accounts.filter(
    a =>
      a.fullName.toLowerCase().includes(search.toLowerCase()) ||
      a.email.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
              Quản trị Hệ thống
            </span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Quản lý Tài khoản Nội bộ</h1>
          <p className="text-xs text-[#6A5C70] mt-0.5">
            Tạo mới, phân quyền vai trò (Role) và khóa/mở khóa tài khoản Bác sĩ, KTV, Nhân viên quầy
          </p>
        </div>

        <button
          onClick={() => setShowAddModal(true)}
          className="px-4 py-2.5 rounded-xl font-bold text-xs text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md transition-all cursor-pointer"
        >
          + Thêm tài khoản
        </button>
      </div>

      {/* Account List Table */}
      <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between gap-4">
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Tìm theo tên hoặc email..."
            className="w-72 p-2.5 border border-card-border rounded-lg text-xs bg-card-bg focus:ring-2 focus:ring-purple-500"
          />
          <span className="text-xs text-[#6A5C70] font-semibold">{filtered.length} tài khoản</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-card-border text-[#6A5C70] uppercase font-bold text-[11px]">
                <th className="py-3 px-4">Họ và Tên</th>
                <th className="py-3 px-4">Email</th>
                <th className="py-3 px-4">Vai trò (Role)</th>
                <th className="py-3 px-4">Khoa / Phòng</th>
                <th className="py-3 px-4">Trạng thái</th>
                <th className="py-3 px-4 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-card-border text-[#2B1D30]">
              {filtered.map(acc => (
                <tr key={acc.id} className="hover:bg-purple-50/50 transition-colors">
                  <td className="py-3.5 px-4 font-bold">{acc.fullName}</td>
                  <td className="py-3.5 px-4">{acc.email}</td>
                  <td className="py-3.5 px-4">
                    <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-purple-100 text-purple-800">
                      {acc.role}
                    </span>
                  </td>
                  <td className="py-3.5 px-4">{acc.department}</td>
                  <td className="py-3.5 px-4">
                    <span
                      className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        acc.status === "ACTIVE"
                          ? "bg-emerald-100 text-emerald-800"
                          : "bg-rose-100 text-rose-800"
                      }`}
                    >
                      {acc.status === "ACTIVE" ? "Hoạt động" : "Đã khóa"}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 text-right">
                    <button
                      onClick={() => handleToggleStatus(acc.id)}
                      className={`px-3 py-1 text-xs font-semibold rounded-md transition-all cursor-pointer ${
                        acc.status === "ACTIVE"
                          ? "bg-rose-50 text-rose-600 hover:bg-rose-100 border border-rose-200"
                          : "bg-emerald-50 text-emerald-600 hover:bg-emerald-100 border border-emerald-200"
                      }`}
                    >
                      {acc.status === "ACTIVE" ? "Khóa" : "Mở khóa"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Add Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-card-bg border border-card-border rounded-xl p-6 w-full max-w-md shadow-xl space-y-4">
            <h3 className="text-base font-bold text-[#2B1D30]">Tạo tài khoản nội bộ mới</h3>

            <form onSubmit={handleCreateAccount} className="space-y-3 text-xs">
              <div>
                <label className="block font-bold mb-1 text-[#2B1D30]">Họ và tên:</label>
                <input
                  type="text"
                  required
                  value={newFullName}
                  onChange={e => setNewFullName(e.target.value)}
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-xs"
                />
              </div>

              <div>
                <label className="block font-bold mb-1 text-[#2B1D30]">Email:</label>
                <input
                  type="email"
                  required
                  value={newEmail}
                  onChange={e => setNewEmail(e.target.value)}
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-xs"
                />
              </div>

              <div>
                <label className="block font-bold mb-1 text-[#2B1D30]">Vai trò (Role):</label>
                <select
                  value={newRole}
                  onChange={e => setNewRole(e.target.value as AccountItem["role"])}
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-xs"
                >
                  <option value="DOCTOR">DOCTOR (Bác sĩ)</option>
                  <option value="LAB_TECHNICIAN">LAB_TECHNICIAN (Kỹ thuật viên)</option>
                  <option value="STAFF">STAFF (Nhân viên quầy/dược)</option>
                  <option value="ADMIN">ADMIN (Quản trị viên)</option>
                </select>
              </div>

              <div>
                <label className="block font-bold mb-1 text-[#2B1D30]">Khoa / Phòng:</label>
                <input
                  type="text"
                  value={newDept}
                  onChange={e => setNewDept(e.target.value)}
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-xs"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 border border-card-border text-xs font-bold text-[#6A5C70] rounded-lg hover:bg-gray-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-[#6E2582] text-white text-xs font-bold rounded-lg hover:bg-[#561A66] cursor-pointer"
                >
                  Lưu tài khoản
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
