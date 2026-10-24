import {api} from "./api";
import {PrescriptionResponse} from "./prescription-api";
export interface VoiceMedicine {spokenName:string;strength:string|null;medicineCode:string|null;candidates:{medicineCode:string;name:string;unit:string}[];fieldConflicts:Record<string,{text:string}[]>;issues:string[];quantity:number|null;quantityUnit:string|null;dose:string|null;frequency:string|null;route:string|null;duration:string|null;timing:string|null;minInterval:string|null;maxDailyDose:string|null;evidence:Record<string,{text:string}>}
export interface VoiceResult {transcript:string;transcriptRevision:number;warnings:string[];items:VoiceMedicine[];modelInfo:Record<string,unknown>;prescriptionDraft:{items:unknown[]};timings?:{decodeMs?:number;asrMs?:number;audioDurationSeconds?:number;extractionMs:number;totalMs:number}}
export interface VoiceJob {jobId:string;consultationId:string;version:number;transcriptRevision:number;state:string;result:VoiceResult|null;errorCode:string|null;expiresAt:string}
export const voicePrescriptionApi={
  capabilities:()=>api.get<{voicePrescription:boolean;clinicalEvidence:boolean}>("/api/ai/capabilities"),
  create:(consultationId:string,key:string,input:Blob|string,signal:AbortSignal)=>{const options={signal,headers:{"Idempotency-Key":key}};if(typeof input==="string")return api.post<VoiceJob>("/api/ai/voice-prescription/jobs",{consultationId,transcript:input},options);const form=new FormData();form.append("consultationId",consultationId);form.append("audio",input,"recording."+(input.type.includes("mp4")?"m4a":"webm"));return api.upload<VoiceJob>("/api/ai/voice-prescription/jobs",form,options);},
  get:(id:string,signal:AbortSignal)=>api.get<VoiceJob>(`/api/ai/voice-prescription/jobs/${id}`,{signal}),
  revise:(job:VoiceJob,transcript:string,signal:AbortSignal)=>api.post<VoiceJob>(`/api/ai/voice-prescription/jobs/${job.jobId}/revisions`,{expectedVersion:job.version,transcript,append:false},{signal}),
  draft:(job:VoiceJob,signal:AbortSignal)=>api.post<PrescriptionResponse>("/api/prescriptions/ai-drafts",{jobId:job.jobId,transcriptRevision:job.transcriptRevision},{signal}),
  editableDraft:(job:VoiceJob,signal:AbortSignal,prescriptionId?:string|null,replaceItems=false)=>api.post<PrescriptionResponse>("/api/prescriptions/ai-drafts/editable",{jobId:job.jobId,transcriptRevision:job.transcriptRevision,...(prescriptionId?{prescriptionId,...(replaceItems?{replaceItems:true}:{})}:{})},{signal}),
};
