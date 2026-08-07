"use client";

import { useState, useEffect, useRef } from "react";
import { directoryApi, DepartmentItem } from "@/lib/directory-api";

interface AccountItem {
  id: string;
  fullName: string;
  email: string;
  role: "DOCTOR" | "LAB_TECHNICIAN" | "STAFF" | "ADMIN";
  department: string;
  status: "ACTIVE" | "LOCKED";
}

const INITIAL_ACCOUNTS: AccountItem[] = [
  { id: "1", fullName: "BS. Nguyễn Văn An", email: "an.nguyen@careflow.vn", role: "DOCTOR", department: "Khoa Nội tổng quát", status: "ACTIVE" },
  { id: "2", fullName: "BS. Trần Thị Bình", email: "binh.tran@careflow.vn", role: "DOCTOR", department: "Khoa Nhi", status: "ACTIVE" },
  { id: "3", fullName: "KTV. Lê Hoàng Nam", email: "nam.le@careflow.vn", role: "LAB_TECHNICIAN", department: "Khoa Xét nghiệm", status: "ACTIVE" },
  { id: "4", fullName: "NV. Phạm Minh Thu", email: "thu.pham@careflow.vn", role: "STAFF", department: "Quầy Tiếp nhận", status: "ACTIVE" },
];

const ROLE_OPTIONS: { value: AccountItem["role"]; label: string }[] = [
  { value: "DOCTOR", label: "Bác sĩ" },
  { value: "LAB_TECHNICIAN", label: "Kỹ thuật viên Cận lâm sàng" },
  { value: "STAFF", label: "Nhân viên Tiếp nhận & Dược" },
  { value: "ADMIN", label: "Quản trị viên" },
];

export default function AdminAccountsPage() {
  const [, setIsLoading] = useState(true);
  const [accounts, setAccounts] = useState<AccountItem[] | null>(null);
  const [search, setSearch] = useState("");
  const [showAddModal, setShowAddModal] = useState(false);
  const [confirmAccount, setConfirmAccount] = useState<AccountItem | null>(null);

  // Form states
  const [newFullName, setNewFullName] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newRole, setNewRole] = useState<AccountItem["role"]>("DOCTOR");
  const [showRoleDropdown, setShowRoleDropdown] = useState(false);
  const roleSelectRef = useRef<HTMLDivElement>(null);
  const [newDept, setNewDept] = useState("Khoa Nội tổng quát");

  // Real Data Departments Combobox
  const [departments, setDepartments] = useState<DepartmentItem[]>([]);
  const [selectedDept, setSelectedDept] = useState<DepartmentItem | null>(null);
  const [deptSearch, setDeptSearch] = useState("");
  const [showDeptDropdown, setShowDeptDropdown] = useState(false);
  const deptComboboxRef = useRef<HTMLDivElement>(null);

  // Close dropdowns on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (deptComboboxRef.current && !deptComboboxRef.current.contains(event.target as Node)) {
        setShowDeptDropdown(false);
      }
      if (roleSelectRef.current && !roleSelectRef.current.contains(event.target as Node)) {
        setShowRoleDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    async function loadData() {
      setIsLoading(true);
      try {
        const [deptsList, doctorProfiles] = await Promise.all([
          directoryApi.getDepartments(),
          directoryApi.getDoctors(),
        ]);

        setDepartments(deptsList ?? []);
        if ((deptsList ?? []).length > 0) {
          setSelectedDept(deptsList[0]);
          setNewDept(deptsList[0].name);
        }

        if (doctorProfiles && doctorProfiles.length > 0) {
          const realAccounts: AccountItem[] = doctorProfiles.map((doc, idx) => ({
            id: doc.id || String(idx + 1),
            fullName: doc.title ? `${doc.title} ${doc.fullName}` : doc.fullName,
            email: `${doc.fullName.toLowerCase().replace(/[^a-z0-9]/g, ".")}@careflow.vn`,
            role: "DOCTOR",
            department: doc.specialization || doc.departmentName || "Khoa Nội tổng quát",
            status: doc.isActive !== false ? "ACTIVE" : "LOCKED",
          }));
          setAccounts([...realAccounts, ...INITIAL_ACCOUNTS.filter(a => a.role !== "DOCTOR")]);
        } else {
          setAccounts(INITIAL_ACCOUNTS);
        }
      } catch {
        setAccounts(INITIAL_ACCOUNTS);
      } finally {
        setIsLoading(false);
      }
    }
    loadData();
  }, []);

  const handleConfirmToggleStatus = () => {
    if (!confirmAccount) return;
    setAccounts(prev =>
      (prev ?? []).map(acc =>
        acc.id === confirmAccount.id ? { ...acc, status: acc.status === "ACTIVE" ? "LOCKED" : "ACTIVE" } : acc
      )
    );
    setConfirmAccount(null);
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

    setAccounts(prev => [newAcc, ...(prev ?? [])]);
    setShowAddModal(false);
    setNewFullName("");
    setNewEmail("");
  };

  const filtered = (accounts ?? []).filter(
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
                <th className="py-3 px-4">Vai trò</th>
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
                  <td className="py-3.5 px-4 font-bold text-[#6E2582]">
                    {acc.role === "DOCTOR" ? "Bác sĩ" : acc.role === "LAB_TECHNICIAN" ? "Kỹ thuật viên CLS" : acc.role === "STAFF" ? "Nhân viên Tiếp nhận/Dược" : "Quản trị viên"}
                  </td>
                  <td className="py-3.5 px-4">{acc.department}</td>
                  <td className="py-3.5 px-4">
                    <div className="flex items-center gap-1.5 text-xs font-semibold">
                      <span className={`w-1.5 h-1.5 rounded-full ${acc.status === "ACTIVE" ? "bg-emerald-500" : "bg-rose-500"}`} />
                      <span className={acc.status === "ACTIVE" ? "text-emerald-700" : "text-rose-700"}>
                        {acc.status === "ACTIVE" ? "Hoạt động" : "Đã khóa"}
                      </span>
                    </div>
                  </td>
                  <td className="py-3.5 px-4 text-right">
                    <button
                      onClick={() => setConfirmAccount(acc)}
                      className="w-20 py-1 text-xs font-semibold rounded-md border border-card-border bg-white text-[#2B1D30] hover:bg-gray-100 transition-all cursor-pointer text-center"
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

      {/* Status Change Confirmation Modal */}
      {confirmAccount && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-card-bg border border-card-border rounded-xl p-6 w-full max-w-md shadow-xl space-y-4">
            <h3 className="text-base font-bold text-[#2B1D30]">
              {confirmAccount.status === "ACTIVE" ? "Xác nhận khóa tài khoản" : "Xác nhận mở khóa tài khoản"}
            </h3>
            <p className="text-xs text-[#6A5C70]">
              Bạn có chắc chắn muốn {confirmAccount.status === "ACTIVE" ? "khóa" : "mở khóa"} tài khoản của nhân viên{" "}
              <strong className="text-[#2B1D30]">{confirmAccount.fullName}</strong> ({confirmAccount.email}) không?
            </p>
            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setConfirmAccount(null)}
                className="px-4 py-2 border border-card-border text-xs font-bold text-[#6A5C70] rounded-lg hover:bg-gray-100 cursor-pointer"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleConfirmToggleStatus}
                className={`px-4 py-2 text-xs font-bold text-white rounded-lg shadow-sm transition-all cursor-pointer ${
                  confirmAccount.status === "ACTIVE"
                    ? "bg-rose-600 hover:bg-rose-700"
                    : "bg-[#6E2582] hover:bg-[#561A66]"
                }`}
              >
                {confirmAccount.status === "ACTIVE" ? "Xác nhận khóa" : "Xác nhận mở khóa"}
              </button>
            </div>
          </div>
        </div>
      )}

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

              {/* Custom Role Dropdown */}
              <div>
                <label className="block font-bold mb-1 text-[#2B1D30]">Vai trò:</label>
                <div className="relative" ref={roleSelectRef}>
                  <button
                    type="button"
                    onClick={() => setShowRoleDropdown(!showRoleDropdown)}
                    className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-xs text-left font-semibold text-[#2B1D30] flex items-center justify-between hover:bg-gray-50 cursor-pointer shadow-sm"
                  >
                    <span>{ROLE_OPTIONS.find(r => r.value === newRole)?.label}</span>
                    <svg className={`w-4 h-4 text-gray-500 transition-transform duration-200 ${showRoleDropdown ? "rotate-180" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                  </button>

                  {showRoleDropdown && (
                    <div className="absolute left-0 right-0 mt-1 bg-white border border-card-border rounded-lg shadow-lg z-50 overflow-hidden divide-y divide-gray-100">
                      {ROLE_OPTIONS.map(opt => (
                        <div
                          key={opt.value}
                          onClick={() => {
                            setNewRole(opt.value);
                            setShowRoleDropdown(false);
                          }}
                          className={`p-2.5 text-xs font-semibold cursor-pointer transition-colors flex items-center justify-between ${
                            newRole === opt.value
                              ? "bg-purple-50 text-[#6E2582] font-bold"
                              : "text-[#2B1D30] hover:bg-purple-50/50"
                          }`}
                        >
                          <span>{opt.label}</span>
                          {newRole === opt.value && (
                            <span className="w-1.5 h-1.5 rounded-full bg-[#6E2582]" />
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Custom Department Combobox Chips */}
              <div>
                <label className="block font-bold mb-1 text-[#2B1D30]">Khoa / Phòng trực thuộc:</label>
                <div className="relative" ref={deptComboboxRef}>
                  {selectedDept ? (
                    <div className="flex items-center justify-between p-2.5 border border-purple-200 rounded-lg bg-purple-50 text-xs shadow-sm">
                      <div className="flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-[#6E2582]" />
                        <span className="font-bold text-[#6E2582]">{selectedDept.name}</span>
                        <span className="text-[10px] text-purple-700 font-mono">({selectedDept.code})</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedDept(null);
                          setNewDept("");
                          setShowDeptDropdown(true);
                        }}
                        className="text-purple-600 hover:text-purple-900 text-xs font-bold px-1.5 py-0.5 rounded-md hover:bg-purple-100 transition-colors cursor-pointer"
                        title="Đổi Khoa khác"
                      >
                        ✕
                      </button>
                    </div>
                  ) : (
                    <div>
                      <div className="relative">
                        <input
                          type="text"
                          placeholder="Gõ từ khóa để lọc nhanh Khoa từ hệ thống..."
                          value={deptSearch}
                          onFocus={() => setShowDeptDropdown(true)}
                          onChange={e => {
                            setDeptSearch(e.target.value);
                            setShowDeptDropdown(true);
                          }}
                          className="w-full p-2.5 pr-8 border border-card-border rounded-lg bg-card-bg text-xs focus:ring-2 focus:ring-purple-500 outline-none shadow-sm"
                        />
                        <svg className={`w-4 h-4 text-gray-500 absolute right-2.5 top-3 transition-transform duration-200 pointer-events-none ${showDeptDropdown ? "rotate-180" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                        </svg>
                      </div>

                      {showDeptDropdown && (
                        <div className="absolute left-0 right-0 mt-1 max-h-48 overflow-y-auto bg-white border border-card-border rounded-lg shadow-lg z-50 divide-y divide-gray-100">
                          {departments
                            .filter(d =>
                              d.name.toLowerCase().includes(deptSearch.toLowerCase()) ||
                              d.code.toLowerCase().includes(deptSearch.toLowerCase())
                            )
                            .map(dept => (
                              <div
                                key={dept.code}
                                onClick={() => {
                                  setSelectedDept(dept);
                                  setNewDept(dept.name);
                                  setShowDeptDropdown(false);
                                  setDeptSearch("");
                                }}
                                className="p-2.5 hover:bg-purple-50 cursor-pointer flex items-center justify-between transition-colors"
                              >
                                <span className="font-bold text-[#2B1D30] text-xs">{dept.name}</span>
                                <span className="text-[10px] text-[#6A5C70] font-mono">{dept.code}</span>
                              </div>
                            ))}
                          {departments.filter(d =>
                            d.name.toLowerCase().includes(deptSearch.toLowerCase()) ||
                            d.code.toLowerCase().includes(deptSearch.toLowerCase())
                          ).length === 0 && (
                            <div className="p-3 text-center text-xs text-gray-500">
                              Không tìm thấy Khoa khớp từ khóa trong hệ thống
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
                <div className="mt-1.5 flex items-center justify-between text-[11px] text-[#6A5C70]">
                  <span>Chỉ chọn các Khoa đã có sẵn trong hệ thống.</span>
                  <a href="/admin/facility" className="text-purple-600 font-bold hover:underline">
                    + Tạo Khoa mới →
                  </a>
                </div>
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
