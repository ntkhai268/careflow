"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  directoryApi,
  DepartmentItem,
  RoomItem,
  DoctorProfileItem,
} from "@/lib/directory-api";

function formatDoctorDisplayName(doc: DoctorProfileItem): string {
  const fullName = (doc.fullName || "").trim();
  const title = (doc.title || "").trim();
  if (!title) return fullName;
  if (fullName.toLocaleLowerCase("vi").startsWith(title.toLocaleLowerCase("vi"))) {
    return fullName;
  }
  return `${title} ${fullName}`;
}

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
  const [docPrimary, setDocPrimary] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
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
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchData();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [fetchData]);

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
    setDocPrimary(false);
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
    setDocPrimary(doc.primaryForBooking === true);
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
          primaryForBooking: docPrimary,
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
          primaryForBooking: docPrimary,
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
      <div className="flex flex-col justify-between gap-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs md:flex-row md:items-center">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-[#0277A8]" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#0277A8]">
              Quản trị Cấu hình Bệnh viện
            </span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-[#17242D]">
            Cấu hình Cơ sở Y tế & Danh bạ Bác sĩ
          </h1>
          <p className="mt-0.5 text-xs text-[#667985]">
            Quản lý danh mục Khoa chuyên môn, Danh sách Phòng khám và Hồ sơ Bác sĩ hành nghề
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Link
            href="/admin/services"
            className="rounded-xl border border-[#8BD2EE] bg-[#DDF3FC] px-3.5 py-2 text-xs font-bold text-[#00577E] transition-colors hover:bg-[#8BD2EE]/40"
          >
            Dịch vụ & bảng giá
          </Link>
          <button
            onClick={openCreateDept}
            disabled={isLoading}
            className="cursor-pointer rounded-xl bg-[#0277A8] px-3.5 py-2 text-xs font-bold text-white shadow-xs transition-all hover:bg-[#00577E] disabled:opacity-50"
          >
            + Thêm khoa
          </button>
          <button
            onClick={openCreateRoom}
            disabled={isLoading}
            className="cursor-pointer rounded-xl border border-[#8BD2EE] bg-[#DDF3FC] px-3.5 py-2 text-xs font-bold text-[#00577E] shadow-xs transition-all hover:bg-[#8BD2EE]/40 disabled:opacity-50"
          >
            + Thêm phòng
          </button>
          <button
            onClick={openCreateDoctor}
            disabled={isLoading}
            className="cursor-pointer rounded-xl border border-[#087F6A]/30 bg-[#D9F3EC] px-3.5 py-2 text-xs font-bold text-[#087F6A] shadow-xs transition-all hover:bg-[#087F6A] hover:text-white disabled:opacity-50"
          >
            + Thêm hồ sơ Bác sĩ
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 p-4 text-xs font-semibold text-[#BA1A1A]">
          {error}
        </div>
      )}

      {/* Main Grid */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Departments Column */}
        <div className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-[#17242D]">Khoa chuyên môn</h2>
            <span className="rounded-full bg-[#DDF3FC] px-2.5 py-0.5 text-xs font-bold text-[#00577E]">
              {(departments ?? []).length} khoa
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 animate-pulse rounded-xl bg-[#F5F8FA]" />
              ))}
            </div>
          ) : (departments ?? []).length === 0 ? (
            <p className="py-6 text-center text-xs text-[#667985]">Chưa có khoa nào được khởi tạo</p>
          ) : (
            <div className="space-y-3 text-xs">
              {(departments ?? []).map((dept) => (
                <div
                  key={dept.code}
                  className="space-y-2 rounded-xl border border-[#8BD2EE]/50 bg-[#DDF3FC]/35 p-4"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-bold text-[#17242D]">{dept.name}</span>
                    <span className="rounded-md bg-[#DDF3FC] px-2 py-0.5 font-mono text-[10px] font-bold text-[#00577E]">
                      {dept.code}
                    </span>
                  </div>
                  {dept.description && (
                    <p className="text-[11px] text-[#4D626E]">{dept.description}</p>
                  )}
                  <div className="flex justify-end pt-1">
                    <button
                      onClick={() => openEditDept(dept)}
                      className="cursor-pointer text-xs font-bold text-[#0277A8] hover:underline"
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
        <div className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-[#17242D]">
              Danh sách Phòng khám & CLS
            </h2>
            <span className="rounded-full bg-[#DDF3FC] px-2.5 py-0.5 text-xs font-bold text-[#00577E]">
              {(rooms ?? []).length} phòng
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="h-16 animate-pulse rounded-xl bg-[#F5F8FA]" />
              ))}
            </div>
          ) : (rooms ?? []).length === 0 ? (
            <p className="py-6 text-center text-xs text-[#667985]">Chưa có phòng nào được tạo</p>
          ) : (
            <div className="space-y-3 text-xs">
              {(rooms ?? []).map((room) => (
                <div
                  key={room.id}
                  className="flex items-center justify-between rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-3.5"
                >
                  <div>
                    <p className="text-sm font-bold text-[#17242D]">{room.displayName}</p>
                    <p className="mt-0.5 text-[11px] text-[#667985]">
                      Mã: <span className="font-mono font-bold text-[#17242D]">{room.id}</span> |
                      Khoa: {room.departmentCode}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <span
                      className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${
                        room.roomType === "CONSULTATION"
                          ? "bg-[#DDF3FC] text-[#00577E]"
                          : room.roomType === "LAB"
                            ? "bg-[#D9F3EC] text-[#087F6A]"
                            : "bg-[#EDF2F5] text-[#4D626E]"
                      }`}
                    >
                      {room.roomType === "CONSULTATION"
                        ? "Phòng khám"
                        : room.roomType === "LAB"
                          ? "Phòng XN"
                          : room.roomType === "IMAGING"
                            ? "CĐHA"
                            : "Nhà thuốc"}
                    </span>
                    <button
                      onClick={() => openEditRoom(room)}
                      className="cursor-pointer text-[11px] font-bold text-[#0277A8] hover:underline"
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
        <div className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-[#17242D]">Danh bạ Hồ sơ Bác sĩ</h2>
            <span className="rounded-full bg-[#D9F3EC] px-2.5 py-0.5 text-xs font-bold text-[#087F6A]">
              {(doctors ?? []).length} bác sĩ
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 animate-pulse rounded-xl bg-[#D9F3EC]/40" />
              ))}
            </div>
          ) : (doctors ?? []).length === 0 ? (
            <p className="py-6 text-center text-xs text-[#667985]">Chưa có hồ sơ bác sĩ nào</p>
          ) : (
            <div className="space-y-3 text-xs">
              {(doctors ?? []).map((doc) => (
                <div
                  key={doc.id}
                  className="space-y-1 rounded-xl border border-[#087F6A]/25 bg-[#D9F3EC]/45 p-3.5"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-bold text-[#17242D]">
                      {formatDoctorDisplayName(doc)}
                    </span>
                    <button
                      onClick={() => openEditDoctor(doc)}
                      className="cursor-pointer text-[11px] font-bold text-[#087F6A] hover:underline"
                    >
                      Sửa hồ sơ
                    </button>
                  </div>
                  <p className="text-[11px] text-[#4D626E]">
                    Chuyên khoa:{" "}
                    <span className="font-semibold text-[#17242D]">
                      {doc.specialization || "Nội tổng quát"}
                    </span>
                  </p>
                  <p className="text-[11px] text-[#4D626E]">
                    Khoa:{" "}
                    <span className="font-semibold text-[#17242D]">
                      {doc.departmentName || doc.departmentCode || "Chưa gán"}
                    </span>{" "}
                    | Phòng:{" "}
                    <span className="font-mono font-bold text-[#00577E]">
                      {doc.assignedRoomId || "Chưa gán"}
                    </span>
                  </p>
                  {doc.licenseNumber && (
                    <p className="font-mono text-[10px] font-semibold text-[#087F6A]">
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0B2534]/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md space-y-4 rounded-2xl border border-[#D5E0E6] bg-white p-6 shadow-2xl">
            <h3 className="text-lg font-extrabold text-[#17242D]">
              {editingDept ? "Chỉnh sửa Khoa chuyên môn" : "Thêm Khoa chuyên môn mới"}
            </h3>

            {formError && (
              <div className="rounded-xl border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 p-3 text-xs font-semibold text-[#BA1A1A]">
                {formError}
              </div>
            )}

            <form onSubmit={handleSaveDept} className="space-y-3 text-xs">
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">Mã Khoa</label>
                <input
                  type="text"
                  disabled={!!editingDept}
                  value={deptCode}
                  onChange={(e) => setDeptCode(e.target.value)}
                  placeholder="VD: INTERNAL, PEDIATRICS"
                  className="w-full rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-2.5 font-mono uppercase focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15 disabled:opacity-60"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">Tên Khoa chuyên môn</label>
                <input
                  type="text"
                  value={deptName}
                  onChange={(e) => setDeptName(e.target.value)}
                  placeholder="VD: Khoa Nội tổng quát"
                  className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">Mô tả / Ghi chú</label>
                <textarea
                  value={deptDesc}
                  onChange={(e) => setDeptDesc(e.target.value)}
                  placeholder="Mô tả nhiệm vụ khoa..."
                  rows={2}
                  className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowDeptModal(false)}
                  className="cursor-pointer rounded-xl px-4 py-2 text-xs font-bold text-[#4D626E] hover:bg-[#EDF2F5]"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="cursor-pointer rounded-xl bg-[#0277A8] px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-[#00577E] disabled:opacity-50"
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0B2534]/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md space-y-4 rounded-2xl border border-[#D5E0E6] bg-white p-6 shadow-2xl">
            <h3 className="text-lg font-extrabold text-[#17242D]">
              {editingRoom ? "Chỉnh sửa Phòng" : "Thêm Phòng mới"}
            </h3>

            {formError && (
              <div className="rounded-xl border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 p-3 text-xs font-semibold text-[#BA1A1A]">
                {formError}
              </div>
            )}

            <form onSubmit={handleSaveRoom} className="space-y-3 text-xs">
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">Mã Phòng</label>
                <input
                  type="text"
                  disabled={!!editingRoom}
                  value={roomId}
                  onChange={(e) => setRoomId(e.target.value)}
                  placeholder="VD: ROOM-01, LAB-01"
                  className="w-full rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-2.5 font-mono uppercase focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15 disabled:opacity-60"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">Khoa trực thuộc</label>
                <select
                  value={roomDeptCode}
                  onChange={(e) => setRoomDeptCode(e.target.value)}
                  className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
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
                <label className="mb-1 block font-bold text-[#17242D]">Tên phòng hiển thị</label>
                <input
                  type="text"
                  value={roomDisplayName}
                  onChange={(e) => setRoomDisplayName(e.target.value)}
                  placeholder="VD: Phòng khám Nội 01"
                  className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">Loại điểm phục vụ</label>
                <select
                  value={roomType}
                  onChange={(e) => setRoomType(e.target.value)}
                  className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
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
                  className="cursor-pointer rounded-xl px-4 py-2 text-xs font-bold text-[#4D626E] hover:bg-[#EDF2F5]"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="cursor-pointer rounded-xl bg-[#0277A8] px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-[#00577E] disabled:opacity-50"
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0B2534]/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md space-y-4 rounded-2xl border border-[#D5E0E6] bg-white p-6 shadow-2xl">
            <h3 className="text-lg font-extrabold text-[#17242D]">
              {editingDoctor
                ? "Chỉnh sửa Hồ sơ Bác sĩ"
                : "Gán Hồ sơ Bác sĩ mới (Sau khi tạo Account)"}
            </h3>

            {formError && (
              <div className="rounded-xl border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 p-3 text-xs font-semibold text-[#BA1A1A]">
                {formError}
              </div>
            )}

            <form onSubmit={handleSaveDoctor} className="space-y-3 text-xs">
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">
                  Mã Tài khoản (User ID từ Identity)
                </label>
                <input
                  type="text"
                  disabled={!!editingDoctor}
                  value={docUserId}
                  onChange={(e) => setDocUserId(e.target.value)}
                  placeholder="VD: d0000001-0000-0000-0000-000000000001"
                  className="w-full rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-2.5 font-mono text-[11px] focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15 disabled:opacity-60"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block font-bold text-[#17242D]">Họ và Tên Bác sĩ</label>
                <input
                  type="text"
                  value={docFullName}
                  onChange={(e) => setDocFullName(e.target.value)}
                  placeholder="VD: Nguyễn Văn An"
                  className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="mb-1 block font-bold text-[#17242D]">Học hàm / Học vị</label>
                  <input
                    type="text"
                    value={docTitle}
                    onChange={(e) => setDocTitle(e.target.value)}
                    placeholder="VD: BS. CKI"
                    className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
                  />
                </div>
                <div>
                  <label className="mb-1 block font-bold text-[#17242D]">Số CCHN</label>
                  <input
                    type="text"
                    value={docLicense}
                    onChange={(e) => setDocLicense(e.target.value)}
                    placeholder="VD: CCHN-012345"
                    className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 font-mono focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="mb-1 block font-bold text-[#17242D]">Khoa làm việc</label>
                  <select
                    value={docDeptCode}
                    onChange={(e) => setDocDeptCode(e.target.value)}
                    className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
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
                  <label className="mb-1 block font-bold text-[#17242D]">Phòng khám gán</label>
                  <select
                    value={docRoomId}
                    onChange={(e) => setDocRoomId(e.target.value)}
                    className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 font-mono focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
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
                <label className="mb-1 block font-bold text-[#17242D]">Chuyên khoa sâu</label>
                <input
                  type="text"
                  value={docSpec}
                  onChange={(e) => setDocSpec(e.target.value)}
                  placeholder="VD: Tim mạch, Nội tiết..."
                  className="w-full rounded-xl border border-[#D5E0E6] bg-white p-2.5 focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
                />
              </div>

              <label className="flex items-start gap-2 text-xs text-[#4D626E]">
                <input
                  type="checkbox"
                  checked={docPrimary}
                  onChange={(e) => setDocPrimary(e.target.checked)}
                  className="mt-0.5 h-4 w-4 accent-[#087F6A]"
                />
                Bác sĩ nhận lịch mặc định của phòng. Khi có nhiều bác sĩ cùng phòng, chỉ chọn một
                người; bỏ chọn người cũ trước khi đổi.
              </label>
              <div className="flex items-center justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowDoctorModal(false)}
                  className="cursor-pointer rounded-xl px-4 py-2 text-xs font-bold text-[#4D626E] hover:bg-[#EDF2F5]"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="cursor-pointer rounded-xl bg-[#087F6A] px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-[#066554] disabled:opacity-50"
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
