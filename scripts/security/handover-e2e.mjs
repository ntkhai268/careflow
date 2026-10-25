// Creates dedicated real audit records through APIs. Never resets or rewrites existing visits.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFile,readFile} from 'node:fs/promises';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const checks=[],clients=[],password='Password123@';let suffix=randomUUID().slice(0,6);
const fixturePath=new URL('../../careflow-mobile-emulator-service/.runtime/handover-e2e.json',import.meta.url);
let fixture;
function pass(name){checks.push({name,status:'PASS'});console.log('PASS '+name);}
async function call(client,path,options={},status=200){const r=await client.request(path,{...options,headers:{...(options.method==='POST'?{'Idempotency-Key':randomUUID()}:{}),...options.headers}});assert.equal(r.status,status,`${path}: ${r.status} ${r.status===status?'':r.json.message}`);return r.json.data;}
async function login(username){const c=new AuditClient();assert.equal((await c.login(username)).status,200);clients.push(c);return c;}
const admin=await login('admin');
async function account(prefix,role){const username=`handover_${prefix}_${suffix}`;const c=new AuditClient();clients.push(c);const existing=await c.login(username);if(existing.status===200){assert.equal(c.tokens.user.role,role);return {client:c,username,id:c.tokens.user.id};}const d=await call(c,'/api/auth/register',{method:'POST',token:null,body:{username,email:username+'@example.test',password}},201);if(role!=='PATIENT')await call(admin,`/api/users/${d.id}/role`,{method:'PATCH',body:{role}});assert.equal((await c.login(username)).status,200);return {client:c,username,id:d.id};}
function payload(c,to){return {consultationId:c.id,toDoctorId:to.id,reason:'Bàn giao ca kiểm thử trên Mac',situation:'Người bệnh demo ổn định, đang khám ngoại trú.',background:'Đã xem hồ sơ demo; đối chiếu dị ứng và các xử trí đã ghi.',assessment:'Tiếp tục đánh giá lâm sàng; các chỉ định chưa phát hành phải theo dõi.',recommendation:'Đối chiếu đúng người bệnh, kiểm tra chỉ định/toa trước khi kết luận.'};}
async function decision(who,h,action,status=200){return call(who.client,`/api/consultations/handovers/${h.id}/${action}`,{method:'POST',body:{note:'Đã đọc và trao đổi nội dung; ghi nhận quyết định kiểm thử.'}},status);}
try {
 const rooms=await call(admin,'/api/directory/rooms'),doctors=await call(admin,'/api/directory/doctors');
 const prepared=process.argv.includes('--resume-preparation')?doctors.find(d=>d.licenseNumber?.startsWith('DEMO-handover_a_')):null;if(prepared)suffix=prepared.licenseNumber.slice('DEMO-handover_a_'.length);
 const room=prepared?rooms.find(r=>r.id===prepared.assignedRoomId):rooms.find(r=>r.roomType==='CONSULTATION'&&r.isActive&&['DA_LIEU','TIM_MACH','CO_XUONG_KHOP','RANG_HAM_MAT'].includes(r.departmentCode)&&!doctors.some(d=>d.isActive&&d.assignedRoomId===r.id));assert.ok(room,'Need an unused configured outpatient room for dedicated real fixtures');
 const a=await account('a','DOCTOR'),b=await account('b','DOCTOR'),c=await account('c','DOCTOR');
 for(const who of [a,b,c])if(!doctors.some(d=>d.userId===who.id))await call(admin,'/api/directory/doctors',{method:'POST',body:{userId:who.id,fullName:'BS Bàn giao '+(who===a?'A':who===b?'B':'C'),title:'BS',departmentCode:room.departmentCode,assignedRoomId:room.id,specialization:'Dữ liệu trình diễn bàn giao',licenseNumber:'DEMO-'+who.username,isActive:true,primaryForBooking:who===a}},201);
 fixture={password,roomId:room.id,department:room.departmentCode,accounts:Object.fromEntries([['a',a],['b',b],['c',c]].map(([k,v])=>[k,{username:v.username,id:v.id}]))};await writeFile(fixturePath,JSON.stringify(fixture,null,2)+'\n',{mode:0o600});
 pass('Three real doctor accounts/profiles share an unused configured room, with explicit primary booking doctor');
 const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
 async function makeVisit(label){const p=await account('patient'+label,'PATIENT');const existingProfile=await p.client.request(`/api/patients/user/${p.id}`);const profile=existingProfile.status===200?existingProfile.json.data:await call(p.client,'/api/patients',{method:'POST',body:{userId:p.id,fullName:'Bệnh nhân bàn giao '+label,dateOfBirth:'1995-01-01',gender:'MALE',address:'Hồ sơ kiểm thử bàn giao trên Mac'}},201);
  const slots=await call(p.client,`/api/appointments/time-slots/availability?department=${room.departmentCode}&date=${date}`);const available=slots.find(s=>s.availableSlots>0||s.available===true||s.remainingCapacity>0);assert.ok(available,'Real available slot required');const timeSlot=available.timeSlot||available.slot||available.time;
  const appointment=await call(p.client,'/api/appointments',{method:'POST',headers:{'Idempotency-Key':`handover-${profile.id}-${date}`},body:{patientId:profile.id,patientName:profile.fullName,department:room.departmentCode,appointmentDate:date,timeSlot,reason:'Kiểm thử bàn giao ca thật qua API'}},201);assert.equal(appointment.doctorId,a.id);
  let ticket;for(let i=0;i<30;i++){const r=await p.client.request(`/api/queues/tickets/appointment/${appointment.id}`);if(r.status===200){ticket=r.json.data;break;}assert.equal(r.status,404);await new Promise(r=>setTimeout(r,300));}assert.ok(ticket);
  await call(admin,'/api/queues/check-in',{method:'POST',body:{ticketCode:ticket.ticketCode,roomId:room.id}});
  return {account:p,patientId:profile.id,appointmentId:appointment.id,ticketId:ticket.ticketId,ticketCode:ticket.ticketCode};}
 const v1=await makeVisit('01'),v2=await makeVisit('02');pass('Two real patients book and check in; multiple doctors do not break primary booking resolution');
 async function start(visit){await call(a.client,`/api/queues/entries/${visit.ticketId}/call`,{method:'POST',body:{}});const ca=await call(a.client,'/api/consultations',{method:'POST',body:{appointmentId:visit.appointmentId,patientId:visit.patientId,doctorId:a.id}},201);await call(a.client,`/api/queues/entries/${visit.ticketId}/start`,{method:'POST',body:{}});visit.consultationId=ca.id;return ca;}
 const ca1=await start(v1);await call(a.client,`/api/consultations/${ca1.id}/clinical-data`,{method:'PUT',body:{symptoms:'Dữ liệu demo bàn giao',clinicalNotes:'Ghi nhận ban đầu bởi bác sĩ A',diagnosis:'Theo dõi ca kiểm thử bàn giao'}});
 const medicines=await call(a.client,'/api/prescriptions/medicines');const medicine=medicines[0];assert.ok(medicine);
 const rxBody=(ca,patient,doctor)=>({consultationId:ca.id,patientId:patient,doctorId:doctor.id,items:[{medicineCode:medicine.code,medicineName:medicine.name,unit:medicine.unit,dosage:'1 đơn vị',frequency:'1 lần/ngày',timing:'Theo hướng dẫn demo',duration:1,quantity:1}]});
 const draft=await call(a.client,'/api/prescriptions',{method:'POST',body:rxBody(ca1,v1.patientId,a)},201);
 const lab=await call(a.client,'/api/labs/orders',{method:'POST',body:{consultationId:ca1.id,patientId:v1.patientId,items:[{serviceCode:'CBC',serviceName:'Công thức máu',servicePointId:'LAB-HEMATOLOGY-01',required:true}],clinicalNote:'Chỉ định thật trong bộ dữ liệu kiểm thử bàn giao'}},201);
 pass('Source doctor creates actual clinical data, prescription draft and Lab order before handover');
 const first=await call(a.client,'/api/consultations/handovers',{method:'POST',body:payload(ca1,b)});
 const replay=await call(a.client,'/api/consultations/handovers',{method:'POST',body:payload(ca1,b)});assert.equal(first.id,replay.id);pass('Repeated pending request is idempotent');
 await call(b.client,`/api/consultations/${ca1.id}`,{},403);await call(b.client,`/api/patients/${v1.patientId}`,{},403);pass('Pending receiver has neither general clinical nor patient-profile access');
 await decision(c,first,'accept',403);await call(admin,'/api/consultations/handovers',{method:'POST',body:payload(ca1,b)},403);pass('Wrong receiver and ADMIN cannot sign or send clinical handovers');
 await decision(b,first,'reject');assert.equal((await call(a.client,`/api/consultations/${ca1.id}`)).doctorId,a.id);pass('Reject keeps original responsibility');
 const cancelled=await call(a.client,'/api/consultations/handovers',{method:'POST',body:payload(ca1,b)});await decision(a,cancelled,'cancel');await decision(b,cancelled,'accept',409);pass('Cancelled request cannot be accepted later');
 const toC=await call(a.client,'/api/consultations/handovers',{method:'POST',body:payload(ca1,c)});await Promise.all([decision(c,toC,'accept'),decision(c,toC,'accept')]);
 const accepted=await call(c.client,`/api/consultations/${ca1.id}`);assert.equal(accepted.doctorId,c.id);assert.equal(accepted.authoredByDoctorId,a.id);await call(a.client,`/api/consultations/${ca1.id}`,{},403);await call(a.client,`/api/consultations/${ca1.id}/clinical-data`,{method:'PUT',body:{diagnosis:'Must not be saved'}},403);await call(a.client,`/api/patients/${v1.patientId}`,{},403);
 pass('Accept changes current authority, keeps original author, revokes former clinical/profile access, and replays safely');
 await call(c.client,`/api/patients/${v1.patientId}`);await call(c.client,`/api/emr/patients/${v1.patientId}/summary`);assert.equal((await call(c.client,`/api/labs/orders/${lab.id}`)).orderedByDoctorId,a.id);assert.ok((await call(c.client,`/api/prescriptions/consultation/${ca1.id}`)).some(p=>p.id===draft.id));await call(c.client,`/api/prescriptions/${draft.id}/confirm`,{method:'POST',body:{}},403);await call(a.client,`/api/prescriptions/${draft.id}/cancel`,{method:'POST',body:{reason:'Must be rejected'}},403);
 pass('Receiver reads real Patient/EMR and earlier Lab/toa, without changing author or signing an inherited draft');
 const ca2=await start(v2);const h1=await call(c.client,'/api/consultations/handovers',{method:'POST',body:payload(ca1,b)}),h2=await call(a.client,'/api/consultations/handovers',{method:'POST',body:payload(ca2,b)});
 const parallel=await Promise.all([h1,h2].map(h=>b.client.request(`/api/consultations/handovers/${h.id}/accept`,{method:'POST',body:{note:'Concurrent actual acceptance'}})));assert.deepEqual(parallel.map(r=>r.status).sort(),[200,409]);pass('Concurrent acceptances from two IN_PROGRESS cases produce exactly one success and one conflict');
 const winner=parallel[0].status===200?{ca:ca1,source:c,h:h1}:{ca:ca2,source:a,h:h2};const loser=parallel[0].status===200?{ca:ca2,source:a,h:h2}:{ca:ca1,source:c,h:h1};
 let back=await call(b.client,'/api/consultations/handovers',{method:'POST',body:payload(winner.ca,winner.source)});await decision(winner.source,back,'accept');await decision(b,loser.h,'accept');back=await call(b.client,'/api/consultations/handovers',{method:'POST',body:payload(loser.ca,loser.source)});await decision(loser.source,back,'accept');
 pass('Conflict leaves a valid pending request that succeeds once receiver workload is free');
 await call(admin,'/api/consultations/handovers/coordinators',{method:'POST',body:{doctorId:c.id,reason:'Ủy quyền điều phối chuyên môn cho bác sĩ demo C'}});
 let exceptional=await call(c.client,'/api/consultations/handovers',{method:'POST',body:payload(ca2,b)});assert.equal(exceptional.exceptional,true);assert.equal(exceptional.fromDoctorId,a.id);assert.equal(exceptional.initiatedBy,c.id);
 await call(admin,`/api/consultations/handovers/coordinators/${c.id}/revoke`,{method:'POST',body:{note:'Kiểm thử thu hồi quyền điều phối'}});await decision(b,exceptional,'accept',409);await decision(c,exceptional,'cancel');pass('Revoking coordination blocks pending exceptional acceptance without impersonating source doctor');
 await call(admin,'/api/consultations/handovers/coordinators',{method:'POST',body:{doctorId:c.id,reason:'Khôi phục ủy quyền cho ca trình diễn'}});exceptional=await call(c.client,'/api/consultations/handovers',{method:'POST',body:payload(ca2,b)});await decision(b,exceptional,'accept');pass('Explicit medical coordinator can initiate takeover, receiver still accepts');
 const ownDraft=await call(b.client,'/api/prescriptions',{method:'POST',body:rxBody(ca2,v2.patientId,b)},201);await call(b.client,`/api/prescriptions/${ownDraft.id}/confirm`,{method:'POST',body:{}});pass('Receiver creates and confirms own signed prescription after takeover');
 await call(b.client,`/api/consultations/${ca2.id}/clinical-data`,{method:'PUT',body:{diagnosis:'Hoàn tất ca kiểm thử bàn giao không cận lâm sàng'}});await call(b.client,`/api/consultations/${ca2.id}/complete`,{method:'POST',body:{}});await call(b.client,`/api/queues/entries/${v2.ticketId}/complete`,{method:'POST',body:{}});
 await call(b.client,`/api/appointments/${v2.appointmentId}/status`,{method:'PUT',body:{status:'COMPLETED'}});assert.equal((await call(v2.account.client,`/api/appointments/${v2.appointmentId}`)).status,'COMPLETED');
 pass('Receiver completes Consultation, original Queue and Appointment; patient sees the completed booking');
 await call(c.client,'/api/consultations/handovers',{method:'POST',body:payload(ca2,b)},409);pass('Closed visit cannot be handed over');
 const history=await call(admin,'/api/consultations/handovers/coordinators/history');assert.ok(history.filter(x=>x.doctorId===c.id).length>=3);pass('Grant, revoke and regrant retain immutable permission audit');
 fixture={createdAt:new Date().toISOString(),password,roomId:room.id,department:room.departmentCode,accounts:Object.fromEntries([['a',a],['b',b],['c',c]].map(([k,v])=>[k,{username:v.username,id:v.id}])),patients:[v1,v2].map(v=>({username:v.account.username,patientId:v.patientId,appointmentId:v.appointmentId,ticketId:v.ticketId,ticketCode:v.ticketCode,consultationId:v.consultationId})),draftId:draft.id,labId:lab.id};
 await writeFile(fixturePath,JSON.stringify(fixture,null,2)+'\n',{mode:0o600});pass('Dedicated real fixtures retained for browser handover and user demonstration');
 const original=await login('mobile_noi_c1d9dc');assert.equal((await call(original,'/api/consultations/5f0a2c7b-ec39-4e39-9083-bcf694df27cd')).status,'IN_PROGRESS');assert.equal((await call(original,'/api/queues/tickets/appointment/65b8e77c-6396-4941-b30f-300e652fd8c6')).ticketCode,'NOI-010');pass('Original user NOI-010 remains IN_PROGRESS and unchanged');
}finally{
 for(const c of clients)if(c.tokens)await c.request('/api/auth/logout',{method:'POST',body:{refreshToken:c.tokens.refreshToken}}).catch(()=>{});
 await writeFile(new URL('../../.planning/consultation-service/handover-api-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,checks},null,2)+'\n');
}
console.log(checks.length+' actual handover checks passed');
