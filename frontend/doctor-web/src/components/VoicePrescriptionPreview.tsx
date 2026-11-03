import { PrescriptionItemRequest } from "@/lib/prescription-api";
import {
  prescriptionItemErrors,
  prescriptionFieldId,
  PrescriptionRequiredField,
} from "@/lib/prescription-validation";
import { VoiceResult } from "@/lib/voice-prescription-api";

interface Props {
  result: VoiceResult | null;
  saved: boolean;
  items: PrescriptionItemRequest[];
  disabled: boolean;
  canEdit: boolean;
  onChange: (index: number, item: PrescriptionItemRequest) => void;
  onRemove: (index: number) => void;
  onEditCatalog: (item: PrescriptionItemRequest) => void;
}

const inputClass =
  "mt-1 w-full min-w-0 rounded-lg border px-3 py-2 text-xs text-[#17242D] outline-none focus:ring-2 disabled:bg-[#F5F8FA] disabled:text-[#667985]";

const neutralInputClass = `${inputClass} border-[#D5E0E6] bg-white focus:border-[#0277A8] focus:ring-[#DDF3FC]`;

function formatDurationText(duration: number | string | null | undefined): string | null {
  if (duration === null || duration === undefined || duration === "") return null;
  const cleaned = String(duration)
    .trim()
    .replace(/^trong\s+/i, "")
    .replace(/\s*ngày$/i, "")
    .trim();
  return cleaned ? `Trong ${cleaned} ngày` : null;
}

function MedicineEditor({
  item,
  index,
  disabled,
  canEdit,
  onChange,
}: {
  item: PrescriptionItemRequest;
  index: number;
  disabled: boolean;
  canEdit: boolean;
  onChange: (item: PrescriptionItemRequest) => void;
}) {
  const update = (patch: Partial<PrescriptionItemRequest>, identity = false) => {
    const details = { ...item.details, ...patch.details };
    delete details.manualReview;
    onChange({ ...item, ...patch, details, ...(identity ? { medicineCode: null } : {}) });
  };
  const errors = canEdit ? prescriptionItemErrors(item) : {};
  const errorCount = Object.keys(errors).length;
  const fieldClass = (key: PrescriptionRequiredField) =>
    `${inputClass} ${
      errors[key]
        ? "border-[#BA1A1A] bg-[#FFDAD6]/35 focus:border-[#BA1A1A] focus:ring-[#FFDAD6]"
        : "border-[#D5E0E6] bg-white focus:border-[#0277A8] focus:ring-[#DDF3FC]"
    }`;
  const errorText = (key: PrescriptionRequiredField) =>
    errors[key] && (
      <span
        id={`${prescriptionFieldId(index, key)}-error`}
        className="mt-1 block text-[11px] font-medium text-[#BA1A1A]"
      >
        {errors[key]}
      </span>
    );
  const text = (
    label: string,
    key: "medicineName" | "unit" | "dosage" | "frequency" | "timing" | "notes",
    identity = false
  ) => {
    if (key === "notes")
      return (
        <label className="text-xs font-medium text-[#4D626E]">
          {label}
          <input
            aria-label={`${label} thuốc ${index + 1}`}
            value={item[key] ?? ""}
            disabled={disabled}
            maxLength={500}
            placeholder="Không bắt buộc"
            onChange={(e) => update({ [key]: e.target.value })}
            className={neutralInputClass}
          />
        </label>
      );
    const id = prescriptionFieldId(index, key);
    return (
      <label
        htmlFor={id}
        className={`text-xs font-medium ${errors[key] ? "text-[#BA1A1A]" : "text-[#4D626E]"}`}
      >
        {label}{" "}
        <span aria-hidden="true" className="text-[#BA1A1A]">
          *
        </span>
        <input
          id={id}
          aria-label={`${label} thuốc ${index + 1}`}
          aria-required={canEdit}
          aria-invalid={!!errors[key]}
          aria-describedby={errors[key] ? `${id}-error` : undefined}
          value={item[key] ?? ""}
          disabled={disabled}
          maxLength={key === "medicineName" ? 255 : key === "unit" ? 50 : 100}
          placeholder="Nhập thông tin"
          onChange={(e) => update({ [key]: e.target.value }, identity)}
          className={fieldClass(key)}
        />
        {errorText(key)}
      </label>
    );
  };
  const detail = (label: string, key: string, identity = false) => (
    <label className="text-xs font-medium text-[#4D626E]">
      {label}
      <input
        aria-label={`${label} thuốc ${index + 1}`}
        value={item.details?.[key] ?? ""}
        disabled={disabled}
        maxLength={200}
        placeholder="Nhập nếu có"
        onChange={(e) =>
          update({ details: { ...item.details, [key]: e.target.value } }, identity)
        }
        className={neutralInputClass}
      />
    </label>
  );
  const number = (label: string, key: "quantity" | "duration") => {
    const id = prescriptionFieldId(index, key);
    return (
      <label
        htmlFor={id}
        className={`text-xs font-medium ${errors[key] ? "text-[#BA1A1A]" : "text-[#4D626E]"}`}
      >
        {label}{" "}
        <span aria-hidden="true" className="text-[#BA1A1A]">
          *
        </span>
        <input
          id={id}
          aria-label={`${label} thuốc ${index + 1}`}
          aria-required={canEdit}
          aria-invalid={!!errors[key]}
          aria-describedby={errors[key] ? `${id}-error` : undefined}
          type="number"
          min="1"
          step="1"
          value={item[key] ?? ""}
          disabled={disabled}
          placeholder="Chưa có"
          onChange={(e) =>
            update({ [key]: e.target.value === "" ? null : Number(e.target.value) })
          }
          className={fieldClass(key)}
        />
        {errorText(key)}
      </label>
    );
  };
  return (
    <details
      open={errorCount > 0}
      className="ml-6 rounded-xl border border-[#8BD2EE]/60 bg-[#F5F8FA] p-3"
    >
      <summary className="cursor-pointer text-xs font-semibold text-[#00577E] select-none">
        Chỉnh sửa chi tiết liều dùng
        {errorCount > 0 && (
          <span className="ml-2 font-medium text-[#BA1A1A]">
            · Còn {errorCount} ô bắt buộc cần bổ sung
          </span>
        )}
      </summary>
      <div className="mt-3 grid min-w-0 gap-3 sm:grid-cols-2">
        {text("Tên thuốc", "medicineName", true)}
        {detail("Hàm lượng", "strength", true)}
        {number("Số lượng", "quantity")}
        {text("Đơn vị", "unit")}
        {text("Liều mỗi lần", "dosage")}
        {text("Tần suất / điều kiện dùng", "frequency")}
        {text("Thời điểm dùng", "timing")}
        {number("Số ngày", "duration")}
        {detail("Dạng bào chế", "dosageForm", true)}
        {detail("Đường dùng", "route")}
        {detail("Khoảng cách tối thiểu", "minInterval")}
        {detail("Liều tối đa/ngày", "maxDailyDose")}
        {text("Ghi chú", "notes")}
      </div>
    </details>
  );
}

export default function VoicePrescriptionPreview({
  result,
  saved,
  items,
  disabled,
  canEdit,
  onChange,
  onRemove,
  onEditCatalog,
}: Props) {
  const rows = saved
    ? items.map((item) => {
        const strength = item.details?.strength;
        const name =
          strength &&
          !item.medicineName
            .replace(/\s/g, "")
            .toLowerCase()
            .includes(strength.replace(/\s/g, "").toLowerCase())
            ? `${item.medicineName} ${strength}`
            : item.medicineName;
        return {
          name,
          quantity: item.quantity,
          unit: item.unit,
          instructions: [
            item.dosage,
            item.frequency,
            item.timing,
            formatDurationText(item.duration),
            item.details?.route,
            item.details?.minInterval,
            item.details?.maxDailyDose,
          ]
            .filter(Boolean)
            .join("; "),
          item,
        };
      })
    : (result?.items || []).map((source) => ({
        name: [source.spokenName, source.strength].filter(Boolean).join(" "),
        quantity: source.quantity,
        unit: source.quantityUnit,
        instructions: [
          source.dose,
          source.frequency,
          source.timing,
          formatDurationText(source.duration),
          source.route,
        ]
          .filter(Boolean)
          .join("; "),
        item: null,
      }));
  return (
    <div aria-label="Bản xem trước toa thuốc" className="min-w-0 font-sans">
      {rows.length === 0 ? (
        <div className="flex min-h-44 flex-col items-center justify-center rounded-xl border border-dashed border-[#D5E0E6] bg-[#F5F8FA] p-6 text-center">
          <p className="text-sm font-semibold text-[#17242D]">Toa thuốc sẽ hiển thị tại đây</p>
          <p className="mt-1.5 text-xs text-[#667985] max-w-sm leading-relaxed">
            Bấm &quot;+ THÊM THUỐC&quot; hoặc dùng thanh ghi âm giọng nói bên trên rồi bấm Lưu toa nháp.
          </p>
        </div>
      ) : (
        <>
          <div className="mb-3 grid grid-cols-[1fr_5rem_3.5rem] items-center gap-2 border-b border-[#D5E0E6] pb-2.5 text-[11px] font-bold uppercase tracking-wider text-[#4D626E]">
            <h4 className="whitespace-nowrap">THÔNG TIN ĐƠN THUỐC</h4>
            <span className="whitespace-nowrap text-right">Số lượng</span>
            <span className="whitespace-nowrap text-right">Đơn vị</span>
          </div>
          <div className="divide-y divide-[#EDF2F5]">
            {rows.map((row, index) => (
              <article key={index} className="py-3.5 first:pt-1 space-y-2.5">
                <div className="grid grid-cols-[1.5rem_1fr_5rem_3.5rem] gap-2 items-baseline text-sm">
                  <span className="font-mono font-bold text-[#0277A8]">{index + 1}.</span>
                  <strong className="min-w-0 break-words font-bold text-[#17242D]">
                    {row.name}
                  </strong>
                  <strong className="whitespace-nowrap text-right font-mono font-bold text-[#00577E]">
                    {row.quantity ?? "—"}
                  </strong>
                  <span className="whitespace-nowrap text-right text-xs font-medium text-[#4D626E]">
                    {row.unit || "—"}
                  </span>
                  <p className="col-start-2 col-end-5 text-xs text-[#4D626E] leading-relaxed bg-[#F5F8FA] px-2.5 py-1.5 rounded-lg border border-[#EDF2F5]">
                    {row.instructions ||
                      "Bổ sung cách dùng trong các ô bên dưới trước khi ký."}
                  </p>
                </div>
                {row.item && (
                  <>
                    {canEdit && (
                      <div className="ml-6 flex justify-end gap-3">
                        {row.item.medicineCode && (
                          <button
                            type="button"
                            disabled={disabled}
                            onClick={() => onEditCatalog(row.item!)}
                            className="text-[11px] font-semibold text-[#0277A8] hover:underline disabled:opacity-50 cursor-pointer"
                          >
                            Chọn lại từ danh mục
                          </button>
                        )}
                        <button
                          type="button"
                          disabled={disabled}
                          aria-label={`Xóa thuốc ${index + 1}`}
                          onClick={() => onRemove(index)}
                          className="text-[11px] font-semibold text-[#BA1A1A] hover:underline disabled:opacity-50 cursor-pointer"
                        >
                          Xóa thuốc
                        </button>
                      </div>
                    )}
                    <MedicineEditor
                      item={row.item}
                      index={index}
                      disabled={disabled}
                      canEdit={canEdit}
                      onChange={(item) => onChange(index, item)}
                    />
                  </>
                )}
              </article>
            ))}
          </div>
        </>
      )}
      {saved && canEdit && rows.length > 0 && (
        <p className="mt-3 border-t border-[#EDF2F5] pt-3 text-[11px] leading-relaxed text-[#667985]">
          Bấm &quot;Chỉnh sửa chi tiết liều dùng&quot; ở từng thuốc nếu cần điều chỉnh, sau đó bấm Lưu toa nháp.
        </p>
      )}
    </div>
  );
}
