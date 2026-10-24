import type {PrescriptionItemRequest} from "./prescription-api";

export const prescriptionRequiredLabels={medicineName:"Tên thuốc",unit:"Đơn vị",dosage:"Liều mỗi lần",frequency:"Tần suất / điều kiện dùng",timing:"Thời điểm dùng",duration:"Số ngày",quantity:"Số lượng"} as const;
export type PrescriptionRequiredField=keyof typeof prescriptionRequiredLabels;

export function prescriptionItemErrors(item:PrescriptionItemRequest){
  const errors:Partial<Record<PrescriptionRequiredField,string>>={};
  for(const key of ["medicineName","unit","dosage","frequency","timing"] as const){
    if(!item[key]?.trim())errors[key]=`Bổ sung ${prescriptionRequiredLabels[key].toLocaleLowerCase("vi-VN")} trước khi ký.`;
  }
  for(const key of ["duration","quantity"] as const){
    const value=item[key];
    if(value==null||!Number.isInteger(value)||value<=0)errors[key]=`${prescriptionRequiredLabels[key]} phải là số nguyên lớn hơn 0.`;
  }
  return errors;
}

export const prescriptionFieldId=(index:number,key:PrescriptionRequiredField)=>`prescription-item-${index}-${key}`;
