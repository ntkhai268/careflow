import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required: changes only the dedicated handover doctor fixture assignment, then restores it.');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const f=JSON.parse(await readFile(new URL('../../careflow-mobile-emulator-service/.runtime/handover-e2e.json',import.meta.url),'utf8'));
const checks=[],clients=[];let originalA,changed=false;
function pass(name){checks.push({name,status:'PASS'});console.log('PASS '+name);}
async function call(client,path,options={},status=200){const r=await client.request(path,{...options,headers:{'Idempotency-Key':randomUUID(),...options.headers}});assert.equal(r.status,status,path+': '+r.status+' '+(r.status===status?'':r.json.message));return r.json.data;}
async function login(name){const c=new AuditClient();clients.push(c);assert.equal((await c.login(name)).status,200);return c;}
const admin=await login('admin'),a=await login(f.accounts.a.username),b=await login(f.accounts.b.username),c=await login(f.accounts.c.username);
const body={consultationId:f.patients[0].consultationId,toDoctorId:f.accounts.a.id,reason:'Kiểm tra thay đổi phân công khi đang chờ',situation:'Bệnh nhân demo đang được bác sĩ B khám.',background:'Giữ nguyên hồ sơ gốc của A.',assessment:'Cần kiểm tra lại phòng trước khi nhận.',recommendation:'Không nhận khi phân công đã đổi.'};
async function restore(){if(changed){await call(admin,`/api/directory/doctors/${originalA.id}`,{method:'PUT',body:originalA});changed=false;}}
try{
 originalA=await call(admin,`/api/directory/doctors/${f.accounts.a.id}`);
 const pending=await call(b,'/api/consultations/handovers',{method:'POST',body});
 const work=await call(c,'/api/consultations/handovers');assert.equal(work.coordinator,true);assert.ok(work.handovers.some(h=>h.id===pending.id));pass('Scoped coordinator sees a pending request initiated by another doctor in the same room');
 await call(admin,`/api/directory/doctors/${originalA.id}`,{method:'PUT',body:{...originalA,departmentCode:'TAI_MUI_HONG',assignedRoomId:'ROOM-06',primaryForBooking:false}});changed=true;
 await call(a,`/api/consultations/handovers/${pending.id}/accept`,{method:'POST',body:{note:'Must not accept after moving rooms'}},403);assert.equal((await call(b,`/api/consultations/${f.patients[0].consultationId}`)).doctorId,f.accounts.b.id);pass('Real changed-room receiver is rejected against authoritative Queue; current responsibility is unchanged');
 await restore();pass('Dedicated fixture doctor assignment and primary booking flag restored through authorized Directory API');
 const cancelled=await call(c,`/api/consultations/handovers/${pending.id}/cancel`,{method:'POST',body:{note:'Điều phối hủy yêu cầu chờ do phân công bên nhận thay đổi.'}});assert.equal(cancelled.status,'CANCELLED');assert.equal(cancelled.decidedBy,f.accounts.c.id);assert.equal((await call(b,`/api/consultations/${f.patients[0].consultationId}`)).doctorId,f.accounts.b.id);pass('Coordinator cancels the stalled request with own actor/reason, without transferring or signing for the receiver');
 const next=await call(b,'/api/consultations/handovers',{method:'POST',body:{...body,toDoctorId:f.accounts.c.id,reason:'Chuẩn bị trình diễn bàn giao qua giao diện sau hồi quy'}});await call(c,`/api/consultations/handovers/${next.id}/accept`,{method:'POST',body:{note:'Đã đối chiếu và nhận lại ca để kiểm thử giao diện.'}});pass('Valid acknowledged handover prepares the same real visit for final browser regression');
}finally{await restore();for(const cl of clients)if(cl.tokens)await cl.request('/api/auth/logout',{method:'POST',body:{refreshToken:cl.tokens.refreshToken}}).catch(()=>{});await writeFile(new URL('../../.planning/consultation-service/handover-coordinator-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,checks},null,2)+'\n');}
