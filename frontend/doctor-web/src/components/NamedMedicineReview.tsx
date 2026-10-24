import {PrescriptionItemRequest} from "@/lib/prescription-api";

interface Props {items:PrescriptionItemRequest[];disabled:boolean;onChange:(items:PrescriptionItemRequest[])=>void}

export default function NamedMedicineReview({items,disabled,onChange}:Props){
  if(!items.length)return null;
  const confirmed=items.every(item=>item.details?.manualReview==="confirmed");
  const complete=items.every(item=>!!item.medicineName?.trim()&&!!item.unit?.trim());
  return <div id="prescription-review" role="group" aria-label="Xác nhận toàn bộ đơn thuốc" className={`mt-5 rounded-xl border-2 p-4 ${confirmed?"border-emerald-200 bg-emerald-50":"border-amber-300 bg-amber-50"}`}>
    <div className="flex flex-wrap items-center justify-between gap-2"><h4 className="text-sm font-semibold text-slate-900">Xác nhận toàn bộ đơn thuốc trước khi ký</h4><span className={`text-xs font-medium ${confirmed?"text-emerald-800":"text-amber-900"}`}>{confirmed?"Đã kiểm tra":"Chưa xác nhận"}</span></div>
    <label htmlFor="prescription-review-confirm" className="mt-3 flex cursor-pointer items-start gap-3 text-sm font-medium leading-relaxed text-slate-900"><input id="prescription-review-confirm" type="checkbox" aria-label="Đã kiểm tra toàn bộ đơn thuốc" checked={confirmed} disabled={disabled||!complete} onChange={e=>onChange(items.map(item=>({...item,details:{...item.details,manualReview:e.target.checked?"confirmed":""}})))} className="mt-1 size-4 shrink-0 accent-purple-700"/>Tôi đã kiểm tra tên thuốc, đơn vị và cách dùng của tất cả thuốc trong đơn.</label>
    <p className="mt-2 text-xs leading-relaxed text-slate-600">{complete?"Xác nhận một lần cho toàn đơn rồi bấm Lưu toa nháp hoặc Ký & xác nhận đơn. Thêm, xóa hoặc sửa thuốc sẽ cần xác nhận lại.":"Bổ sung tên và đơn vị ở các dòng thuốc còn thiếu trước khi xác nhận toàn đơn."}</p>
  </div>;
}
