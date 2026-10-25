"use client";
import {forwardRef,useEffect,useImperativeHandle,useRef,useState,ReactNode} from "react";
import {fixWebmDuration} from "@fix-webm-duration/fix";
import {voicePrescriptionApi,VoiceJob,VoiceResult} from "@/lib/voice-prescription-api";
import {PrescriptionResponse} from "@/lib/prescription-api";
export interface VoiceDraftHandle {save:(replaceItems?:boolean)=>Promise<PrescriptionResponse|null>;pending:()=>boolean;open:(message?:string)=>void;stop:()=>Promise<void>;collapse:()=>void}
interface Props {consultationId:string;actorId:string;prescriptionId:string|null;disabled:boolean;hasPrescription:boolean;canEdit:boolean;onSaveDraft:()=>Promise<boolean>;children:ReactNode}
const timeLabel=(seconds:number)=>{const value=Math.max(0,Math.round(seconds));return `${Math.floor(value/60).toString().padStart(2,"0")}:${(value%60).toString().padStart(2,"0")}`;};
const validDuration=(value:unknown):value is number=>typeof value==="number"&&Number.isFinite(value)&&value>0&&value<=181;
const failure=(e:unknown)=>e instanceof Error?e.message:"Chưa xử lý được giọng nói.";
const VoicePrescriptionPanel=forwardRef<VoiceDraftHandle,Props>(function VoicePrescriptionPanel({consultationId,actorId,prescriptionId,disabled,hasPrescription,canEdit,onSaveDraft,children},ref){
  const [available,setAvailable]=useState(false),[capError,setCapError]=useState(""),[recording,setRecording]=useState(false),[seconds,setSeconds]=useState(0),[busy,setBusy]=useState(""),[error,setError]=useState(""),[text,setText]=useState(""),[hasAudio,setHasAudio]=useState(false),[warnings,setWarnings]=useState<string[]>([]),[textMode,setTextMode]=useState(false);
  const [review,setReview]=useState<VoiceResult|null>(null);
  const [audioUrl,setAudioUrl]=useState("");
  const [clipDuration,setClipDuration]=useState<number|null>(null),[savedDuration,setSavedDuration]=useState<number|null>(null),[finishing,setFinishing]=useState(false),[level,setLevel]=useState<number|null>(null);
  const duration=useRef<number|null>(null),recordingStarted=useRef(0),finalizing=useRef(false),meter=useRef<AudioContext|null>(null),analyser=useRef<AnalyserNode|null>(null);
  const rememberDuration=(value:number|null)=>{duration.current=value;setClipDuration(value);};
  const stopMeter=()=>{const current=meter.current;meter.current=null;analyser.current=null;if(current&&current.state!=="closed")void current.close().catch(()=>{});};
  const [inputPending,setInputPending]=useState(false),[starting,setStarting]=useState(false);
  const pendingInput=useRef(false),acquiring=useRef(false),captureEpoch=useRef(0);
  const panel=useRef<HTMLElement|null>(null),previewUrl=useRef("");
  const metadataKey=`careflow.voice.saved:${actorId}:${consultationId}`;
  const preview=(blob:Blob|null)=>{if(previewUrl.current)URL.revokeObjectURL(previewUrl.current);previewUrl.current=blob?URL.createObjectURL(blob):"";setAudioUrl(previewUrl.current);};
  useEffect(()=>()=>{if(previewUrl.current)URL.revokeObjectURL(previewUrl.current);},[]);
  const recorder=useRef<MediaRecorder|null>(null),stream=useRef<MediaStream|null>(null),chunks=useRef<Blob[]>([]),audio=useRef<Blob|null>(null),stopped=useRef<Promise<Blob>|null>(null),job=useRef<VoiceJob|null>(null),submittedText=useRef(""),key=useRef(crypto.randomUUID()),controller=useRef(new AbortController()),task=useRef<Promise<PrescriptionResponse|null>|null>(null),mounted=useRef(true),created=useRef<PrescriptionResponse|null>(null);
  useEffect(()=>{mounted.current=true;controller.current=new AbortController();voicePrescriptionApi.capabilities().then(r=>{if(mounted.current)setAvailable(r.data.voicePrescription);},e=>{if(mounted.current)setCapError(failure(e));});return()=>{mounted.current=false;controller.current.abort();if(recorder.current?.state==="recording")recorder.current.stop();stream.current?.getTracks().forEach(t=>t.stop());chunks.current=[];audio.current=null;stopMeter();};},[]);
  useEffect(()=>{
    if(!prescriptionId||job.current)return;
    let active=true;
    try{
      const saved=JSON.parse(sessionStorage.getItem(metadataKey)||"null");
      if(saved?.prescriptionId!==prescriptionId)return;
      if(!pendingInput.current&&!acquiring.current&&validDuration(saved.audioDurationSeconds))setSavedDuration(saved.audioDurationSeconds);
      if(typeof saved.jobId!=="string"||!/^[0-9a-f-]{36}$/i.test(saved.jobId))return;
      voicePrescriptionApi.get(saved.jobId,controller.current.signal).then(r=>{
        if(active&&!pendingInput.current&&!acquiring.current&&r.data.consultationId===consultationId&&r.data.state==="SUCCEEDED"&&r.data.result){setText(r.data.result.transcript);setReview(r.data.result);setWarnings(r.data.result.warnings||[]);if(validDuration(r.data.result.timings?.audioDurationSeconds))setSavedDuration(r.data.result.timings.audioDurationSeconds);}
      },()=>{try{sessionStorage.setItem(metadataKey,JSON.stringify({prescriptionId:saved.prescriptionId,...(validDuration(saved.audioDurationSeconds)?{audioDurationSeconds:saved.audioDurationSeconds}:{})}));}catch{}});
    }catch{/* Storage is optional; prescription data remains on the server. */}
    return()=>{active=false;};
  },[prescriptionId,metadataKey,consultationId]);
  useEffect(()=>{
    if(!recording)return;
    const timer=setInterval(()=>{
      setSeconds(Math.floor((performance.now()-recordingStarted.current)/1000));
      const current=analyser.current;if(current){const values=new Uint8Array(current.fftSize);current.getByteTimeDomainData(values);let sum=0;for(const value of values)sum+=((value-128)/128)**2;setLevel(Math.sqrt(sum/values.length));}
    },200);
    return()=>clearInterval(timer);
  },[recording]);
  useEffect(()=>{if(seconds>=180&&recording&&recorder.current?.state==="recording")recorder.current.stop();},[seconds,recording]);
  useEffect(()=>{if(hasPrescription&&!canEdit&&recorder.current?.state==="recording")recorder.current.stop();},[hasPrescription,canEdit]);
  const resetInput=(pending:boolean,mode=false)=>{pendingInput.current=pending;setInputPending(pending);audio.current=null;preview(null);setHasAudio(false);setText("");setTextMode(mode);setWarnings([]);setReview(null);setError("");job.current=null;created.current=null;submittedText.current="";key.current=crypto.randomUUID();setSeconds(0);rememberDuration(null);setLevel(null);};
  const stop=async()=>{if(recorder.current?.state==="recording")recorder.current.stop();return stopped.current;};
  const start=async()=>{
    if(acquiring.current||finalizing.current||task.current||recorder.current?.state==="recording"||disabled||busy||!available||hasPrescription&&!canEdit)return;
    acquiring.current=true;setStarting(true);setError("");const epoch=++captureEpoch.current;let captured:MediaStream|undefined;
    try{
      if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia||typeof MediaRecorder==="undefined")throw new Error("Trình duyệt cần HTTPS/localhost và hỗ trợ ghi âm.");
      const s=await navigator.mediaDevices.getUserMedia({audio:true});captured=s;
      if(!s.getAudioTracks().some(t=>t.readyState==="live"&&t.enabled))throw new Error("Micro chưa cung cấp âm thanh. Hãy chọn đúng micro trong trình duyệt rồi thử lại.");
      if(!mounted.current||controller.current.signal.aborted||captureEpoch.current!==epoch){s.getTracks().forEach(t=>t.stop());return;}
      const mime=["audio/webm;codecs=opus","audio/mp4","audio/webm"].find(t=>MediaRecorder.isTypeSupported(t));const m=new MediaRecorder(s,mime?{mimeType:mime}:undefined),parts:Blob[]=[];let size=0,began=0;
      const ended=new Promise<Blob>((resolve,reject)=>{
        m.ondataavailable=e=>{if(e.data.size){size+=e.data.size;parts.push(e.data);if(size>20*1024*1024&&m.state==="recording")m.stop();}};
        m.onerror=()=>{s.getTracks().forEach(t=>t.stop());stopMeter();if(mounted.current&&recorder.current===m){setRecording(false);setError("Ghi âm bị gián đoạn. Hãy ghi lại.");}reject(new Error("Ghi âm bị gián đoạn"));};
        m.onstop=async()=>{
          s.getTracks().forEach(t=>t.stop());stopMeter();const elapsed=(performance.now()-began)/1000;const raw=new Blob(parts,{type:m.mimeType});parts.length=0;
          if(mounted.current&&recorder.current===m){finalizing.current=true;setFinishing(true);setRecording(false);rememberDuration(elapsed);}
          try{
            if(!raw.size)throw new Error("Micro không tạo được dữ liệu âm thanh. Hãy kiểm tra quyền micro rồi ghi lại.");
            let b=raw;if(raw.type.includes("webm"))b=await fixWebmDuration(raw,elapsed*1000,{logger:false});
            if(mounted.current&&recorder.current===m){audio.current=b;preview(b);setHasAudio(true);recorder.current=null;}resolve(b);
          }catch(e){if(mounted.current&&recorder.current===m){setError(failure(e));recorder.current=null;setHasAudio(false);}reject(e);}
          finally{finalizing.current=false;if(mounted.current)setFinishing(false);}
        };
      });void ended.catch(()=>{});
      m.start(1000);began=performance.now();recordingStarted.current=began;resetInput(true);recorder.current=m;stream.current=s;chunks.current=parts;stopped.current=ended;setRecording(true);
      try{const context=new AudioContext();meter.current=context;const a=context.createAnalyser();a.fftSize=512;context.createMediaStreamSource(s).connect(a);analyser.current=a;void context.resume().catch(()=>{});}catch{stopMeter();}
    }catch(e){captured?.getTracks().forEach(t=>t.stop());if(mounted.current)setError(failure(e));}
    finally{acquiring.current=false;if(mounted.current)setStarting(false);}
  };
  const execute=async(replaceItems=false)=>{if(!pendingInput.current||created.current)return null;if(!audio.current&&recorder.current?.state!=="recording"&&!text.trim())return null;if(hasPrescription&&!canEdit)throw new Error("Chỉ ghi thêm vào toa đang ở dạng nháp.");setError("");setBusy("Đang dừng ghi âm…");const signal=controller.current.signal;try{
    if(recorder.current?.state==="recording")await stop();if(signal.aborted)throw new DOMException("Phiên đã thay đổi","AbortError");
    setBusy("Đang nhận dạng và đọc toa…");let current=job.current;
    if(current&&text.trim()&&text.trim()!==submittedText.current){current=(await voicePrescriptionApi.revise(current,text.trim(),signal)).data;submittedText.current=text.trim();}
    if(!current){const input=textMode?text.trim():audio.current||text.trim();if(!input)throw new Error("Chưa có bản ghi hoặc transcript.");current=(await voicePrescriptionApi.create(consultationId,key.current,input,signal)).data;submittedText.current=typeof input==="string"?input:"";}
    job.current=current;const deadline=Date.now()+240000;
    while(["QUEUED","RUNNING"].includes(current.state)){if(Date.now()>deadline)throw new Error("AI vẫn đang xử lý. Bấm Lưu toa nháp để kiểm tra lại công việc hiện tại.");await new Promise<void>((resolve,reject)=>{const timer=setTimeout(done,800);function done(){signal.removeEventListener("abort",abort);resolve();}function abort(){clearTimeout(timer);reject(new DOMException("Phiên đã thay đổi","AbortError"));}signal.addEventListener("abort",abort,{once:true});});current=(await voicePrescriptionApi.get(current.jobId,signal)).data;job.current=current;}
    if(current.result){setText(current.result.transcript);submittedText.current=current.result.transcript;setWarnings(current.result.warnings||[]);setReview(current.result);}
    if(current.state!=="SUCCEEDED")throw new Error(`Không tạo được đề xuất (${current.errorCode||current.state}). Hãy ghi lại hoặc nhập transcript mới.`);
    if(!current.result?.items.length)throw new Error("Chưa nhận được tên thuốc. Hãy bổ sung tên thuốc trong transcript rồi lưu lại.");
    setBusy(hasPrescription?"Đang thêm thuốc vào toa nháp…":"Đang lưu toa nháp…");const saved=(await voicePrescriptionApi.editableDraft(current,signal,prescriptionId,replaceItems)).data;if(signal.aborted)throw new DOMException("Phiên đã thay đổi","AbortError");created.current=saved;pendingInput.current=false;setInputPending(false);setSavedDuration(validDuration(current.result?.timings?.audioDurationSeconds)?current.result.timings.audioDurationSeconds:duration.current);try{sessionStorage.setItem(metadataKey,JSON.stringify({jobId:current.jobId,prescriptionId:saved.id,...(validDuration(current.result?.timings?.audioDurationSeconds)?{audioDurationSeconds:current.result.timings.audioDurationSeconds}:validDuration(duration.current)?{audioDurationSeconds:duration.current}:{})}));}catch{}audio.current=null;setHasAudio(false);return saved;
  }catch(e){if(mounted.current){setError(failure(e));panel.current?.scrollIntoView({behavior:"smooth",block:"start"});}throw e;}finally{if(mounted.current)setBusy("");}};
  useImperativeHandle(ref,()=>({collapse:()=>close(),pending:()=>acquiring.current||pendingInput.current&&(finalizing.current||!!audio.current||recorder.current?.state==="recording"||!!text.trim()||!!job.current),open:message=>{if(message)setError(message);panel.current?.scrollIntoView({behavior:"smooth",block:"start"});},stop:async()=>{if(acquiring.current)throw new Error("Micro đang được mở. Hãy chờ rồi bấm lưu lại.");await stop();},save:(replaceItems=false)=>{if(task.current)return task.current;task.current=execute(replaceItems).finally(()=>{task.current=null;});return task.current;}}));
  const close=()=>{if(busy)return;captureEpoch.current++;if(recorder.current?.state==="recording")recorder.current.stop();};
  const upload=(file?:File)=>{if(!file||disabled||busy||recording||starting||finalizing.current||!available||hasPrescription&&!canEdit)return;if(!file.size||file.size>20*1024*1024){setError("Audio phải có dữ liệu và không quá 20 MiB.");return;}resetInput(true);audio.current=file;preview(file);setHasAudio(true);panel.current?.scrollIntoView({behavior:"smooth",block:"start"});};
  if(!available&&!hasPrescription)return <>{children}<div className="rounded-xl border bg-white p-3 text-xs text-slate-500">Nhập toa giọng nói chưa được bật.{capError&&<p role="alert">{capError}</p>}</div></>;
  const workspace=<div className="space-y-4"><div className="space-y-4 rounded-2xl border border-purple-100 bg-white p-5">
    <div><h3 className="font-semibold text-slate-900">Nội dung chỉ định</h3><p className="mt-1 text-xs leading-relaxed text-slate-500">Bật micro, đọc toa rồi bấm Dừng ghi âm để nghe lại. Lưu toa nháp để nhận dạng và thêm thuốc.</p></div>
    <div className="flex flex-wrap gap-2"><button type="button" disabled={disabled||!!busy||starting||finishing||!available||hasPrescription&&!canEdit} onClick={()=>recording?void stop().catch(e=>setError(failure(e))):void start()} className="rounded-xl bg-purple-700 px-4 py-3 text-sm font-medium text-white disabled:opacity-50">{starting?"Đang mở micro…":finishing?"Đang chuẩn bị bản ghi…":recording?`Dừng ghi âm (${timeLabel(seconds)})`:hasAudio?"Ghi lại":hasPrescription?"Ghi thêm vào toa nháp":"Bật ghi âm AI"}</button><label className="cursor-pointer rounded-xl border border-slate-200 px-3 py-3 text-sm text-slate-700">Tải audio<input aria-label="Tải audio" type="file" accept="audio/*" className="hidden" disabled={disabled||!!busy||recording||starting||finishing||!available||hasPrescription&&!canEdit} onChange={e=>{upload(e.target.files?.[0]);e.target.value="";}}/></label><button type="button" disabled={recording||starting||finishing||!!busy||!available||hasPrescription&&!canEdit||disabled} onClick={()=>{resetInput(true,true);panel.current?.scrollIntoView({behavior:"smooth",block:"start"});}} className="rounded-xl border border-slate-200 px-3 py-3 text-sm">{hasPrescription?"Nhập đoạn mới":"Nhập transcript"}</button>{inputPending&&!recording&&<button type="button" disabled={!!busy||starting||finishing} onClick={()=>resetInput(false)} className="rounded-xl border border-slate-200 px-3 py-3 text-sm">Bỏ đoạn mới</button>}</div>
    <p className="text-xs text-slate-500">Tối đa 3 phút / 20 MiB mỗi lượt. Audio được xử lý local trên máy chủ CareFlow.</p>
    {recording&&<div className="rounded-xl border border-red-200 bg-red-50 p-3"><p role="status" className="font-medium text-red-800">Đang ghi âm · {timeLabel(seconds)} / 03:00</p>{level!==null&&<><meter aria-label="Mức âm thanh micro" min={0} max={1} value={Math.min(1,level*5)} className="mt-2 w-full"/><p className="text-xs text-slate-600">{level>.01?"Micro đang thu âm thanh":seconds>=2?"Chưa phát hiện tiếng rõ — kiểm tra micro và thử nói gần hơn.":"Micro đang mở…"}</p></>}</div>}
    {finishing&&<p role="status" className="text-sm text-purple-800">Đang chuẩn bị bản ghi {clipDuration!==null?timeLabel(clipDuration):""} để nghe lại…</p>}
    {hasAudio&&!recording&&!finishing&&<div aria-label="Bản ghi chưa thêm vào toa" className="rounded-xl border border-amber-200 bg-amber-50 p-3"><p className="text-sm font-semibold text-amber-900">{clipDuration!==null?`Đã ghi ${timeLabel(clipDuration)}`:"Đã có audio"} · Chưa thêm vào toa nháp</p><p className="mt-1 text-xs text-amber-900">Nghe lại rồi bấm Lưu toa nháp để xử lý. Ghi lại sẽ thay đoạn chưa lưu này.</p></div>}
    {!inputPending&&hasPrescription&&savedDuration!==null&&<p aria-label="Thời lượng bản ghi đã xử lý" className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800">Đã xử lý đoạn ghi âm · {timeLabel(savedDuration)}</p>}
    {hasPrescription&&!canEdit&&<p className="text-xs text-slate-500">Toa đã ký hoặc bị khóa.</p>}
    {audioUrl&&<audio aria-label="Nghe lại bản ghi" controls preload="metadata" src={audioUrl} onLoadedMetadata={e=>{const value=e.currentTarget.duration;if(audio.current&&validDuration(value))rememberDuration(value);}} className="w-full"/>}
    {hasPrescription&&!inputPending&&!text?<p className="rounded-lg bg-slate-50 p-3 text-xs leading-relaxed text-slate-500">Các thuốc đã lưu hiển thị ở Đơn thuốc bên dưới. Bấm Ghi thêm vào toa nháp hoặc Nhập đoạn mới để tiếp tục.</p>:(textMode||!!text||hasAudio)&&<label className="block text-xs">{hasPrescription&&!inputPending?"Đoạn đọc đã lưu gần nhất — chỉnh thuốc bên dưới":"Transcript đoạn mới — có thể sửa trước khi lưu"}<textarea aria-label="Transcript toa thuốc" placeholder={hasAudio&&!text?"Transcript sẽ hiển thị sau khi nhận dạng audio.":"Nhập lời đọc hoặc ghi âm để nhận transcript…"} value={text} disabled={!!busy||starting||finishing||hasPrescription&&!inputPending||disabled||recording||hasAudio&&!text} maxLength={12000} onChange={e=>{pendingInput.current=true;setInputPending(true);setText(e.target.value);setReview(null);setWarnings([]);setError("");if(job.current&&["FAILED","CANCELLED","EXPIRED"].includes(job.current.state))job.current=null;if(!job.current)key.current=crypto.randomUUID();}} className={`mt-1 block w-full rounded-lg border bg-white p-3 text-sm min-h-32`}/></label>}
    {busy&&<p role="status" aria-live="polite" className="text-sm text-purple-800">{busy}</p>}{error&&<p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {review?.timings&&<p className="text-xs text-slate-500">{review.timings.asrMs!==undefined?`Nhận dạng ${(review.timings.asrMs/1000).toFixed(2)} giây · `:""}Đọc toa {(review.timings.extractionMs/1000).toFixed(2)} giây</p>}
    {warnings.length>0&&<details className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900"><summary className="cursor-pointer">{warnings.length} đoạn cần kiểm tra trong transcript</summary><ul className="mt-3 space-y-2">{warnings.map((w,i)=><li key={i}>{w}</li>)}</ul></details>}
    {inputPending&&<button type="button" disabled={disabled||!!busy||starting||finishing||hasPrescription&&!canEdit||!hasPrescription&&!recording&&!hasAudio&&!text.trim()} onClick={()=>void onSaveDraft()} className="w-full rounded-xl bg-purple-700 px-4 py-3 text-sm font-semibold text-white disabled:opacity-50">{busy?"ĐANG LƯU...":"XỬ LÝ & LƯU TOA NHÁP"}</button>}
  </div>{children}</div>;
  return <section ref={panel} aria-label="Nhập toa bằng giọng nói" className="space-y-3 rounded-xl border border-purple-200 bg-purple-50 p-4"><h3 className="font-semibold text-purple-950">Nhập toa bằng giọng nói</h3>{workspace}</section>;
});
export default VoicePrescriptionPanel;
