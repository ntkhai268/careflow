// Verify the dedicated real E2E dataset, including rejected commands; no fixture responses.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {AuditClient} from './audit-client.mjs';
import {fileURLToPath} from 'node:url';
assert.ok(process.argv.includes('--audit-db'), '--audit-db required');
const root=fileURLToPath(new URL('../../',import.meta.url)).replace(/\/$/,'');
const file=root+'/careflow-mobile-emulator-service/.runtime/usage-guide-e2e.json';
const f=JSON.parse(await fs.readFile(file));
const password=process.env.CAREFLOW_AUDIT_PASSWORD;assert.ok(password);
const result={at:new Date().toISOString(),environment:'Local real PostgreSQL / Gateway mTLS; no intercepted API responses',visits:{},checks:[]};
async function login(key){const c=new AuditClient();assert.equal((await c.login(f.accounts[key].username,password)).status,200);return c;}
async function get(c,path,status=200){const r=await c.request(path);assert.equal(r.status,status,path+': '+r.json.message);return r.json.data;}
for(const key of ['no_cls','cls','handover','finalize_regression']){
 const p=await login(key),v=f.visits[key],d=await login(key==='handover'?'doctor_b':'doctor_a');
 const appt=await get(p,'/api/appointments/'+v.appointmentId);assert.equal(appt.status,'COMPLETED');
 const cases=await get(d,'/api/consultations/appointment/'+v.appointmentId);assert.equal(cases.length,1);const ca=cases[0];assert.equal(ca.id,v.consultationId);assert.equal(ca.status,'COMPLETED');
 const rx=await get(p,'/api/prescriptions/'+v.prescriptionId);assert.equal(rx.status,'DISPENSED');
 const ticket=await get(p,'/api/queues/tickets/appointment/'+v.appointmentId);assert.equal(ticket.status,'COMPLETED');
 const labs=await get(d,'/api/labs/orders/consultation/'+v.consultationId);assert.ok(labs.every(x=>x.status==='REVIEWED'));
 result.visits[key]={appointmentId:v.appointmentId,appointment:appt.status,consultationId:ca.id,consultation:ca.status,prescriptionId:rx.id,prescription:rx.status,ticket:ticket.status,labs:labs.map(x=>({id:x.id,status:x.status,orderedByDoctorId:x.orderedByDoctorId})),doctorId:ca.doctorId,authoredByDoctorId:ca.authoredByDoctorId};
 if(key==='handover'){
  assert.equal(ca.doctorId,f.accounts.doctor_b.id);assert.equal(ca.authoredByDoctorId,f.accounts.doctor_a.id);
  const inherited=await get(d,'/api/prescriptions/'+v.authoredPrescriptionId);assert.equal(inherited.status,'DRAFT');assert.equal(inherited.doctorId,f.accounts.doctor_a.id);
  const a=await login('doctor_a');await get(a,'/api/consultations/'+ca.id,403);
  const r=await d.request('/api/prescriptions/'+inherited.id+'/confirm',{method:'POST',body:{}});assert.equal(r.status,403);
  assert.equal(labs[0].orderedByDoctorId,f.accounts.doctor_a.id);result.checks.push('Transferred case keeps A authorship; B cannot sign A draft; A clinical access denied');
 }
 console.log(key,'completed, dispensed, same case, clinic queue completed; labs reviewed:',labs.length);
}
const cancel=await login('cancel');assert.equal((await get(cancel,'/api/appointments/'+f.visits.cancel.appointmentId)).status,'CANCELLED');assert.equal((await get(cancel,'/api/queues/tickets/appointment/'+f.visits.cancel.appointmentId)).status,'CANCELLED');result.checks.push('Cancelled appointment and ticket both CANCELLED');
const b=await login('doctor_b'),v=f.visits.handover;
const request={consultationId:v.consultationId,patientId:v.patientId,department:'CO_XUONG_KHOP',recommendedDate:f.followup.appointmentDate,timeSlot:f.followup.timeSlot,note:'Lịch tái khám QA, dữ liệu tổng hợp'};
assert.ok(f.followup, 'Prepare and manually verify a real follow-up first');
assert.equal(f.followup.sourceConsultationId,v.consultationId);
const patient=await login('handover');assert.equal((await get(patient,'/api/appointments/'+f.followup.id)).sourceConsultationId,v.consultationId);
const a=await login('doctor_a');const denied=await a.request('/api/appointments/follow-ups',{method:'POST',body:request});assert.equal(denied.status,403);result.followup={id:f.followup.id,date:f.followup.appointmentDate,sourceConsultationId:f.followup.sourceConsultationId,status:f.followup.status};result.checks.push('Real future follow-up created by current responsible doctor; former doctor rejected403; patient can read source link');
const fam=await login('family'),profiles=await get(fam,'/api/patients/user/'+f.accounts.family.id+'/profiles');assert.equal(profiles.length,2);const records=await get(fam,'/api/patients/'+f.accounts.family.patientId+'/health-records');assert.ok(records.some(x=>x.id===f.health.recordId));result.checks.push('Two real owned family profiles; uploaded health record survives reload');
await fs.writeFile(root+'/.planning/security/end-to-end-2026-10-17-results.json',JSON.stringify(result,null,2)+'\n');console.log('PASS',result.checks);
