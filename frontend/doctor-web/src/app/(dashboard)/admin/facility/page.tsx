"use client";

import { useEffect, useState } from "react";
import {
  directoryApi,
  DepartmentItem,
  RoomItem,
  DoctorProfileItem,
} from "@/lib/directory-api";

export default function AdminFacilityPage() {
  const [departments, setDepartments] = useState<DepartmentItem[] | null>(null);
  const [rooms, setRooms] = useState<RoomItem[] | null>(null);
  const [doctors, setDoctors] = useState<DoctorProfileItem[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modals state
  const [showDeptModal, setShowDeptModal] = useState(false);
  const [editingDept, setEditingDept] = useState<DepartmentItem | null>(null);
  const [deptCode, setDeptCode] = useState("");
  const [deptName, setDeptName] = useState("");
  const [deptDesc, setDeptDesc] = useState("");

  const [showRoomModal, setShowRoomModal] = useState(false);
  const [editingRoom, setEditingRoom] = useState<RoomItem | null>(null);
  const [roomId, setRoomId] = useState("");
  const [roomDeptCode, setRoomDeptCode] = useState("");
  const [roomDisplayName, setRoomDisplayName] = useState("");
  const [roomType, setRoomType] = useState("CONSULTATION");

  const [showDoctorModal, setShowDoctorModal] = useState(false);
  const [editingDoctor, setEditingDoctor] = useState<DoctorProfileItem | null>(null);
  const [docUserId, setDocUserId] = useState("");
  const [docFullName, setDocFullName] = useState("");
  const [docTitle, setDocTitle] = useState("BS. CKI");
  const [docDeptCode, setDocDeptCode] = useState("");
  const [docRoomId, setDocRoomId] = useState("");
  const [docSpec, setDocSpec] = useState("");
  const [docLicense, setDocLicense] = useState("");

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const fetchData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [deptList, roomList, docList] = await Promise.all([
        directoryApi.getDepartments(),
        directoryApi.getRooms(),
        directoryApi.getDoctors(),
      ]);
      setDepartments(deptList ?? []);
      setRooms(roomList ?? []);
      setDoctors(docList ?? []);
    } catch {
      setError("Không thể tải dữ liệu cấu hình cơ sở y tế. Vui lòng thử lại.");
      setDepartments([]);
      setRooms([]);
      setDoctors([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // Department Modal Handler
  const openCreateDept = () => {
    setEditingDept(null);
    setDeptCode("");
    setDeptName("");
    setDeptDesc("");
    setFormError(null);
    setShowDeptModal(true);
  };

  const openEditDept = (dept: DepartmentItem) => {
    setEditingDept(dept);
    setDeptCode(dept.code);
    setDeptName(dept.name);
    setDeptDesc(dept.description || "");
    setFormError(null);
    setShowDeptModal(true);
  };

  const handleSaveDept = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!deptName.trim()) return;
    setIsSubmitting(true);
    setFormError(null);
    try {
      if (editingDept) {
        await directoryApi.updateDepartment(editingDept.code, {
          name: deptName.trim(),
          description: deptDesc.trim(),
        });
      } else {
        await directoryApi.createDepartment({
          code: deptCode.trim().toUpperCase(),
          name: deptName.trim(),
          description: deptDesc.trim(),
        });
      }
      setShowDeptModal(false);
      await fetchData();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Thao tác lưu khoa thất bại.";
      setFormError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Room Modal Handler
  const openCreateRoom = () => {
    setEditingRoom(null);
    setRoomId("");
    setRoomDeptCode(departments && departments.length > 0 ? departments[0].code : "");
    setRoomDisplayName("");
    setRoomType("CONSULTATION");
    setFormError(null);
    setShowRoomModal(true);
  };

  const openEditRoom = (room: RoomItem) => {
    setEditingRoom(room);
    setRoomId(room.id);
    setRoomDeptCode(room.departmentCode);
    setRoomDisplayName(room.displayName);
    setRoomType(room.roomType);
    setFormError(null);
    setShowRoomModal(true);
  };

  const handleSaveRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!roomDisplayName.trim() || !roomDeptCode) return;
    setIsSubmitting(true);
    setFormError(null);
    try {
      if (editingRoom) {
        await directoryApi.updateRoom(editingRoom.id, {
          departmentCode: roomDeptCode,
          displayName: roomDisplayName.trim(),
          roomType: roomType,
        });
      } else {
        await directoryApi.createRoom({
          id: roomId.trim().toUpperCase(),
          departmentCode: roomDeptCode,
          displayName: roomDisplayName.trim(),
          roomType: roomType,
        });
      }
      setShowRoomModal(false);
      await fetchData();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Thao tác lưu phòng thất bại.";
      setFormError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Doctor Profile Handler
  const openCreateDoctor = () => {
    setEditingDoctor(null);
    setDocUserId("");
    setDocFullName("");
    setDocTitle("BS. CKI");
    setDocDeptCode(departments && departments.length > 0 ? departments[0].code : "");
    setDocRoomId("");
    setDocSpec("Nội tổng quát");
    setDocLicense("CCHN-" + Math.floor(100000 + Math.random() * 900000));
    setFormError(null);
    setShowDoctorModal(true);
  };

  const openEditDoctor = (doc: DoctorProfileItem) => {
    setEditingDoctor(doc);
    setDocUserId(doc.userId);
    setDocFullName(doc.fullName);
    setDocTitle(doc.title || "BS");
    setDocDeptCode(doc.departmentCode || "");
    setDocRoomId(doc.assignedRoomId || "");
    setDocSpec(doc.specialization || "");
    setDocLicense(doc.licenseNumber || "");
    setFormError(null);
    setShowDoctorModal(true);
  };

  const handleSaveDoctor = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!docFullName.trim() || (!editingDoctor && !docUserId.trim())) return;
    setIsSubmitting(true);
    setFormError(null);
    try {
      if (editingDoctor) {
        await directoryApi.updateDoctorProfile(editingDoctor.id, {
          fullName: docFullName.trim(),
          title: docTitle.trim(),
          departmentCode: docDeptCode,
          assignedRoomId: docRoomId.trim(),
          specialization: docSpec.trim(),
          licenseNumber: docLicense.trim(),
        });
      } else {
        await directoryApi.createDoctorProfile({
          userId: docUserId.trim(),
          fullName: docFullName.trim(),
          title: docTitle.trim(),
          departmentCode: docDeptCode,
          assignedRoomId: docRoomId.trim(),
          specialization: docSpec.trim(),
          licenseNumber: docLicense.trim(),
        });
      }
      setShowDoctorModal(false);
      await fetchData();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Thao tác lưu hồ sơ bác sĩ thất bại.";
      setFormError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
              Quản trị Cấu hình Bệnh viện
            </span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Cấu hình Cơ sở Y tế & Danh bạ Bác sĩ</h1>
          <p className="text-xs text-[#6A5C70] mt-0.5">
            Quản lý danh mục Khoa chuyên môn, Danh sách Phòng khám và Hồ sơ Bác sĩ hành nghề
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={openCreateDept}
            disabled={isLoading}
            className="px-3.5 py-2 rounded-xl font-bold text-xs text-white bg-[#6E2582] hover:bg-[#561A66] shadow-sm transition-all cursor-pointer disabled:opacity-50"
          >
            + Thêm khoa
          </button>
          <button
            onClick={openCreateRoom}
            disabled={isLoading}
            className="px-3.5 py-2 rounded-xl font-bold text-xs text-[#6E2582] bg-purple-100 hover:bg-purple-200 shadow-sm transition-all cursor-pointer disabled:opacity-50"
          >
            + Thêm phòng
          </button>
          <button
            onClick={openCreateDoctor}
            disabled={isLoading}
            className="px-3.5 py-2 rounded-xl font-bold text-xs text-emerald-800 bg-emerald-100 hover:bg-emerald-200 shadow-sm transition-all cursor-pointer disabled:opacity-50"
          >
            + Thêm hồ sơ Bác sĩ
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 font-semibold">
          {error}
        </div>
      )}

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Departments Column */}
        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Khoa chuyên môn</h2>
            <span className="text-xs text-purple-700 font-bold">
              {(departments ?? []).length} khoa
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 bg-purple-50/50 rounded-xl animate-pulse" />
              ))}
            </div>
          ) : (departments ?? []).length === 0 ? (
            <p className="text-xs text-[#6A5C70] py-6 text-center">Chưa có khoa nào được khởi tạo</p>
          ) : (
            <div className="space-y-3 text-xs">
              {(departments ?? []).map((dept) => (
                <div key={dept.code} className="p-4 bg-purple-50/60 border border-purple-200 rounded-xl space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-purple-950 text-sm">{dept.name}</span>
                    <span className="px-2 py-0.5 bg-purple-200 text-purple-800 font-bold text-[10px] rounded-md">
                      {dept.code}
                    </span>
                  </div>
                  {dept.description && (
                    <p className="text-[#6A5C70] text-[11px]">{dept.description}</p>
                  )}
                  <div className="pt-1 flex justify-end">
                    <button
                      onClick={() => openEditDept(dept)}
                      className="text-xs font-bold text-[#6E2582] hover:underline cursor-pointer"
                    >
                      Chỉnh sửa
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Rooms Column */}
        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh sách Phòng khám & CLS</h2>
            <span className="text-xs text-purple-700 font-bold">
              {(rooms ?? []).length} phòng
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="h-16 bg-gray-100 rounded-xl animate-pulse" />
              ))}
            </div>
          ) : (rooms ?? []).length === 0 ? (
            <p className="text-xs text-[#6A5C70] py-6 text-center">Chưa có phòng nào được tạo</p>
          ) : (
            <div className="space-y-3 text-xs">
              {(rooms ?? []).map((room) => (
                <div key={room.id} className="p-3.5 bg-gray-50 border border-card-border rounded-xl flex items-center justify-between">
                  <div>
                    <p className="font-bold text-[#2B1D30] text-sm">{room.displayName}</p>
                    <p className="text-[#6A5C70] text-[11px] mt-0.5">
                      Mã: <span className="font-mono font-bold text-gray-800">{room.id}</span> | Khoa: {room.departmentCode}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <span className="px-2 py-0.5 bg-purple-100 text-purple-800 font-bold rounded-md text-[10px]">
                      {room.roomType === "CONSULTATION" ? "Phòng khám" : room.roomType === "LAB" ? "Phòng XN" : room.roomType === "IMAGING" ? "CĐHA" : "Nhà thuốc"}
                    </span>
                    <button
                      onClick={() => openEditRoom(room)}
                      className="text-[11px] font-bold text-[#6E2582] hover:underline cursor-pointer"
                    >
                      Chỉnh sửa
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Doctors Column */}
        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh bạ Hồ sơ Bác sĩ</h2>
            <span className="text-xs text-purple-700 font-bold">
              {(doctors ?? []).length} bác sĩ
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 bg-emerald-50/50 rounded-xl animate-pulse" />
              ))}
            </div>
          ) : (doctors ?? []).length === 0 ? (
            <p className="text-xs text-[#6A5C70] py-6 text-center">Chưa có hồ sơ bác sĩ nào</p>
          ) : (
            <div className="space-y-3 text-xs">
              {(doctors ?? []).map((doc) => (
                <div key={doc.id} className="p-3.5 bg-emerald-50/50 border border-emerald-200 rounded-xl space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-emerald-950 text-sm">
                      {doc.title ? `${doc.title} ${doc.fullName}` : doc.fullName}
                    </span>
                    <button
                      onClick={() => openEditDoctor(doc)}
                      className="text-[11px] font-bold text-emerald-700 hover:underline cursor-pointer"
                    >
                      Sửa hồ sơ
                    </button>
                  </div>
                  <p className="text-[#6A5C70] text-[11px]">
                    Chuyên khoa: <span className="font-semibold text-gray-800">{doc.specialization || "Nội tổng quát"}</span>
                  </p>
                  <p className="text-[#6A5C70] text-[11px]">
                    Khoa: <span className="font-semibold text-gray-800">{doc.departmentName || doc.departmentCode || "Chưa gán"}</span> | Phòng: <span className="font-mono font-bold">{doc.assignedRoomId || "Chưa gán"}</span>
                  </p>
                  {doc.licenseNumber && (
                    <p className="text-[10px] text-emerald-800 font-mono">
                      Số CCHN: {doc.licenseNumber}
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Modal Department */}
      {showDeptModal && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl space-y-4">
            <h3 className="text-lg font-bold text-[#2B1D30]">
              {editingDept ? "Chỉnh sửa Khoa chuyên môn" : "Thêm Khoa chuyên môn mới"}
            </h3>

            {formError && (
              <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-semibold">
                {formError}
              </div>
            )}

            <form onSubmit={handleSaveDept} className="space-y-3 text-xs">
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Mã Khoa</label>
                <input
                  type="text"
                  disabled={!!editingDept}
                  value={deptCode}
                  onChange={(e) => setDeptCode(e.target.value)}
                  placeholder="VD: INTERNAL, PEDIATRICS"
                  className="w-full p-2.5 border border-card-border rounded-lg bg-gray-50 focus:ring-2 focus:ring-purple-500 uppercase font-mono disabled:opacity-60"
                  required
                />
              </div>
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Tên Khoa chuyên môn</label>
                <input
                  type="text"
                  value={deptName}
                  onChange={(e) => setDeptName(e.target.value)}
                  placeholder="VD: Khoa Nội tổng quát"
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                  required
                />
              </div>
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Mô tả / Ghi chú</label>
                <textarea
                  value={deptDesc}
                  onChange={(e) => setDeptDesc(e.target.value)}
                  placeholder="Mô tả nhiệm vụ khoa..."
                  rows={2}
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowDeptModal(false)}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-gray-600 hover:bg-gray-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md cursor-pointer disabled:opacity-50"
                >
                  {isSubmitting ? "Đang lưu..." : "Lưu lại"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Room */}
      {showRoomModal && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl space-y-4">
            <h3 className="text-lg font-bold text-[#2B1D30]">
              {editingRoom ? "Chỉnh sửa Phòng" : "Thêm Phòng mới"}
            </h3>

            {formError && (
              <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-semibold">
                {formError}
              </div>
            )}

            <form onSubmit={handleSaveRoom} className="space-y-3 text-xs">
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Mã Phòng</label>
                <input
                  type="text"
                  disabled={!!editingRoom}
                  value={roomId}
                  onChange={(e) => setRoomId(e.target.value)}
                  placeholder="VD: ROOM-01, LAB-01"
                  className="w-full p-2.5 border border-card-border rounded-lg bg-gray-50 focus:ring-2 focus:ring-purple-500 uppercase font-mono disabled:opacity-60"
                  required
                />
              </div>
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Khoa trực thuộc</label>
                <select
                  value={roomDeptCode}
                  onChange={(e) => setRoomDeptCode(e.target.value)}
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                  required
                >
                  {(departments ?? []).map((d) => (
                    <option key={d.code} value={d.code}>
                      {d.name} ({d.code})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Tên phòng hiển thị</label>
                <input
                  type="text"
                  value={roomDisplayName}
                  onChange={(e) => setRoomDisplayName(e.target.value)}
                  placeholder="VD: Phòng khám Nội 01"
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                  required
                />
              </div>
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Loại điểm phục vụ</label>
                <select
                  value={roomType}
                  onChange={(e) => setRoomType(e.target.value)}
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                >
                  <option value="CONSULTATION">Phòng khám Lâm sàng</option>
                  <option value="LAB">Phòng Xét nghiệm</option>
                  <option value="IMAGING">Chẩn đoán Hình ảnh</option>
                  <option value="PHARMACY">Quầy Phát thuốc</option>
                </select>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowRoomModal(false)}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-gray-600 hover:bg-gray-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md cursor-pointer disabled:opacity-50"
                >
                  {isSubmitting ? "Đang lưu..." : "Lưu lại"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Doctor Profile */}
      {showDoctorModal && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl space-y-4">
            <h3 className="text-lg font-bold text-[#2B1D30]">
              {editingDoctor ? "Chỉnh sửa Hồ sơ Bác sĩ" : "Gán Hồ sơ Bác sĩ mới (Sau khi tạo Account)"}
            </h3>

            {formError && (
              <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-semibold">
                {formError}
              </div>
            )}

            <form onSubmit={handleSaveDoctor} className="space-y-3 text-xs">
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Mã Tài khoản (User ID từ Identity)</label>
                <input
                  type="text"
                  disabled={!!editingDoctor}
                  value={docUserId}
                  onChange={(e) => setDocUserId(e.target.value)}
                  placeholder="VD: d0000001-0000-0000-0000-000000000001"
                  className="w-full p-2.5 border border-card-border rounded-lg bg-gray-50 focus:ring-2 focus:ring-purple-500 font-mono text-[11px] disabled:opacity-60"
                  required
                />
              </div>
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Họ và Tên Bác sĩ</label>
                <input
                  type="text"
                  value={docFullName}
                  onChange={(e) => setDocFullName(e.target.value)}
                  placeholder="VD: Nguyễn Văn An"
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block font-bold text-[#2B1D30] mb-1">Học hàm / Học vị</label>
                  <input
                    type="text"
                    value={docTitle}
                    onChange={(e) => setDocTitle(e.target.value)}
                    placeholder="VD: BS. CKI"
                    className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                  />
                </div>
                <div>
                  <label className="block font-bold text-[#2B1D30] mb-1">Số CCHN</label>
                  <input
                    type="text"
                    value={docLicense}
                    onChange={(e) => setDocLicense(e.target.value)}
                    placeholder="VD: CCHN-012345"
                    className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500 font-mono"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block font-bold text-[#2B1D30] mb-1">Khoa làm việc</label>
                  <select
                    value={docDeptCode}
                    onChange={(e) => setDocDeptCode(e.target.value)}
                    className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                  >
                    <option value="">-- Chọn khoa --</option>
                    {(departments ?? []).map((d) => (
                      <option key={d.code} value={d.code}>
                        {d.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block font-bold text-[#2B1D30] mb-1">Phòng khám gán</label>
                  <select
                    value={docRoomId}
                    onChange={(e) => setDocRoomId(e.target.value)}
                    className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500 font-mono"
                  >
                    <option value="">-- Chọn phòng --</option>
                    {(rooms ?? []).map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.displayName} ({r.id})
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label className="block font-bold text-[#2B1D30] mb-1">Chuyên khoa sâu</label>
                <input
                  type="text"
                  value={docSpec}
                  onChange={(e) => setDocSpec(e.target.value)}
                  placeholder="VD: Tim mạch, Nội tiết..."
                  className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowDoctorModal(false)}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-gray-600 hover:bg-gray-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-white bg-emerald-700 hover:bg-emerald-800 shadow-md cursor-pointer disabled:opacity-50"
                >
                  {isSubmitting ? "Đang lưu..." : "Lưu hồ sơ"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
