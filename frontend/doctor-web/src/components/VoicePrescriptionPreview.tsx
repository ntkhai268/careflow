import {PrescriptionItemRequest} from "@/lib/prescription-api";
import {prescriptionItemErrors,prescriptionFieldId,PrescriptionRequiredField} from "@/lib/prescription-validation";
import {VoiceResult} from "@/lib/voice-prescription-api";

interface Props {result:VoiceResult|null;saved:boolean;items:PrescriptionItemRequest[];disabled:boolean;canEdit:boolean;onChange:(index:number,item:PrescriptionItemRequest)=>void;onRemove:(index:number)=>void;onEditCatalog:(item:PrescriptionItemRequest)=>void}
const inputClass="mt-1 w-full min-w-0 rounded-lg border px-3 py-2 text-sm text-slate-900 outline-none focus:ring-2 disabled:bg-slate-50 disabled:text-slate-500";

const neutralInputClass=`${inputClass} border-slate-200 bg-white focus:border-purple-400 focus:ring-purple-100`;

function MedicineEditor({item,index,disabled,canEdit,onChange}:{item:PrescriptionItemRequest;index:number;disabled:boolean;canEdit:boolean;onChange:(item:PrescriptionItemRequest)=>void}){
  const update=(patch:Partial<PrescriptionItemRequest>,identity=false)=>{
    const details={...item.details,...patch.details};delete details.manualReview;
    onChange({...item,...patch,details,...(identity?{medicineCode:null}:{})});
  };
  const errors=canEdit?prescriptionItemErrors(item):{};
  const errorCount=Object.keys(errors).length;
  const fieldClass=(key:PrescriptionRequiredField)=>`${inputClass} ${errors[key]?"border-red-500 bg-red-50 focus:border-red-500 focus:ring-red-100":"border-slate-200 bg-white focus:border-purple-400 focus:ring-purple-100"}`;
  const errorText=(key:PrescriptionRequiredField)=>errors[key]&&<span id={`${prescriptionFieldId(index,key)}-error`} className="mt-1 block text-xs font-medium text-red-700">{errors[key]}</span>;
  const text=(label:string,key:"medicineName"|"unit"|"dosage"|"frequency"|"timing"|"notes",identity=false)=>{
    if(key==="notes")return <label className="text-xs font-medium text-slate-600">{label}<input aria-label={`${label} thuốc ${index+1}`} value={item[key]??""} disabled={disabled} maxLength={500} placeholder="Không bắt buộc" onChange={e=>update({[key]:e.target.value})} className={neutralInputClass}/></label>;
    const id=prescriptionFieldId(index,key);
    return <label htmlFor={id} className={`text-xs font-medium ${errors[key]?"text-red-700":"text-slate-600"}`}>{label} <span aria-hidden="true" className="text-red-600">*</span><input id={id} aria-label={`${label} thuốc ${index+1}`} aria-required={canEdit} aria-invalid={!!errors[key]} aria-describedby={errors[key]?`${id}-error`:undefined} value={item[key]??""} disabled={disabled} maxLength={key==="medicineName"?255:key==="unit"?50:100} placeholder="Nhập thông tin" onChange={e=>update({[key]:e.target.value},identity)} className={fieldClass(key)}/>{errorText(key)}</label>;
  };
  const detail=(label:string,key:string,identity=false)=><label className="text-xs font-medium text-slate-600">{label}<input aria-label={`${label} thuốc ${index+1}`} value={item.details?.[key]??""} disabled={disabled} maxLength={200} placeholder="Nhập nếu có" onChange={e=>update({details:{...item.details,[key]:e.target.value}},identity)} className={neutralInputClass}/></label>;
  const number=(label:string,key:"quantity"|"duration")=>{
    const id=prescriptionFieldId(index,key);
    return <label htmlFor={id} className={`text-xs font-medium ${errors[key]?"text-red-700":"text-slate-600"}`}>{label} <span aria-hidden="true" className="text-red-600">*</span><input id={id} aria-label={`${label} thuốc ${index+1}`} aria-required={canEdit} aria-invalid={!!errors[key]} aria-describedby={errors[key]?`${id}-error`:undefined} type="number" min="1" step="1" value={item[key]??""} disabled={disabled} placeholder="Chưa có" onChange={e=>update({[key]:e.target.value===""?null:Number(e.target.value)})} className={fieldClass(key)}/>{errorText(key)}</label>;
  };
  return <details open={!disabled} className="ml-7 rounded-xl border border-purple-100 bg-purple-50/40 p-3">
    <summary className="cursor-pointer text-xs font-semibold text-purple-800">Chỉnh sửa thuốc{errorCount>0&&<span className="ml-2 font-medium text-red-700">· Còn {errorCount} ô bắt buộc cần bổ sung</span>}</summary>
    <div className="mt-3 grid min-w-0 gap-3 sm:grid-cols-2">
      {text("Tên thuốc","medicineName",true)}{detail("Hàm lượng","strength",true)}
      {number("Số lượng","quantity")}{text("Đơn vị","unit")}
      {text("Liều mỗi lần","dosage")}{text("Tần suất / điều kiện dùng","frequency")}
      {text("Thời điểm dùng","timing")}{number("Số ngày","duration")}
      {detail("Dạng bào chế","dosageForm",true)}{detail("Đường dùng","route")}
      {detail("Khoảng cách tối thiểu","minInterval")}{detail("Liều tối đa/ngày","maxDailyDose")}
      {text("Ghi chú","notes")}
    </div>
  </details>;
}

export default function VoicePrescriptionPreview({result,saved,items,disabled,canEdit,onChange,onRemove,onEditCatalog}:Props){
  const rows=saved?items.map(item=>{
    const strength=item.details?.strength;const name=strength&&!item.medicineName.replace(/\s/g,"").toLowerCase().includes(strength.replace(/\s/g,"").toLowerCase())?`${item.medicineName} ${strength}`:item.medicineName;
    return {name,quantity:item.quantity,unit:item.unit,instructions:[item.dosage,item.frequency,item.timing,item.duration?`Trong ${item.duration} ngày`:null,item.details?.route,item.details?.minInterval,item.details?.maxDailyDose].filter(Boolean).join("; "),item};
  }):(result?.items||[]).map(source=>({name:[source.spokenName,source.strength].filter(Boolean).join(" "),quantity:source.quantity,unit:source.quantityUnit,instructions:[source.dose,source.frequency,source.timing,source.duration,source.route].filter(Boolean).join("; "),item:null}));
  return <div aria-label="Bản xem trước toa thuốc" className="min-w-0">
    {rows.length===0?<div className="flex min-h-56 flex-col items-center justify-center rounded-xl border border-dashed border-slate-200 p-6 text-center"><p className="font-medium text-slate-700">Toa thuốc sẽ hiển thị tại đây</p><p className="mt-2 text-sm text-slate-500">Ghi âm hoặc tải audio, rồi bấm Lưu toa nháp. Các tên thuốc nhận được sẽ thành từng dòng để chỉnh sửa.</p></div>:<>
      <div className="mb-5 grid grid-cols-[1fr_3rem_3rem] gap-2 border-b border-slate-200 pb-3 font-serif"><h4 className="font-bold underline underline-offset-4">THÔNG TIN ĐƠN THUỐC</h4><span className="text-right text-xs">Số lượng</span><span className="text-right text-xs">Đơn vị</span></div>
      {rows.map((row,index)=><article key={index} className="mb-6 space-y-3">
        <div className="grid grid-cols-[1.4rem_1fr_3rem_3rem] gap-2 font-serif text-lg"><span className="font-semibold">{index+1}.</span><strong className="min-w-0 break-words">{row.name}</strong><strong className="text-right">{row.quantity??"—"}</strong><span className="text-right">{row.unit||"—"}</span><p className="col-start-2 col-end-5 text-base italic leading-relaxed">{row.instructions||"Bổ sung cách dùng trong các ô bên dưới trước khi ký."}</p></div>
        {row.item&&<>{canEdit&&<div className="ml-7 flex justify-end gap-3">{row.item.medicineCode&&<button type="button" disabled={disabled} onClick={()=>onEditCatalog(row.item!)} className="text-xs font-medium text-purple-700 disabled:opacity-50">Chọn lại từ danh mục</button>}<button type="button" disabled={disabled} aria-label={`Xóa thuốc ${index+1}`} onClick={()=>onRemove(index)} className="text-xs font-medium text-rose-700 disabled:opacity-50">Xóa thuốc</button></div>}<MedicineEditor item={row.item} index={index} disabled={disabled} canEdit={canEdit} onChange={item=>onChange(index,item)}/></>}
      </article>)}
    </>}
    {saved&&canEdit&&<p className="mt-4 border-t border-slate-100 pt-4 text-xs leading-relaxed text-slate-500">Sửa trực tiếp từng ô rồi bấm Lưu toa nháp. Điền các thông tin còn trống trước khi Ký & xác nhận đơn.</p>}
  </div>;
}
