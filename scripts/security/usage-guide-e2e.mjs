// Dedicated demo records created through the real local Gateway, with persistent IDs for reruns.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db')) throw Error('--audit-db required');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const path=new URL('../../careflow-mobile-emulator-service/.runtime/usage-guide-e2e.json',import.meta.url);
let fixture;try{fixture=JSON.parse(await readFile(path));}catch{fixture={date:new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date()),accounts:{},visits:{},checks:[]};}
const password=process.env.CAREFLOW_AUDIT_PASSWORD;
assert.ok(password, 'Set CAREFLOW_AUDIT_PASSWORD from the private demo configuration');
async function save(){await writeFile(path,JSON.stringify(fixture,null,2)+'\n',{mode:0o600});}
export async function call(c,path,options={},status=200){const r=await c.request(path,{...options,headers:{...(options.method==='POST'?{'Idempotency-Key':randomUUID()}:{}),...options.headers}});assert.equal(r.status,status,`${path}: ${r.status} ${r.json.message}`);return r.json.data;}
const seed=new AuditClient();assert.equal((await seed.login('admin',password)).status,200);
async function account(key,role){const username='e2e_1710_'+key,c=new AuditClient();let existing=await c.login(username,password);let id=existing.json?.data?.user?.id;
 if(existing.status!==200){const u=await call(c,'/api/auth/register',{method:'POST',token:null,body:{username,email:username+'@example.test',password}},201);id=u.id;if(role!=='PATIENT')await call(seed,`/api/users/${id}/role`,{method:'PATCH',body:{role}});assert.equal((await c.login(username,password)).status,200);}
 assert.equal(c.tokens.user.role,role);fixture.accounts[key]={username,id,role};return c;}
const admin=await account('admin','ADMIN'),a=await account('doctor_a','DOCTOR'),b=await account('doctor_b','DOCTOR'),c=await account('doctor_c','DOCTOR'),staff=await account('staff','STAFF'),lab=await account('lab','LAB_TECHNICIAN');
const rooms=await call(admin,'/api/directory/rooms'),doctors=await call(admin,'/api/directory/doctors');
if(!fixture.room){const room=rooms.find(r=>r.id==='ROOM-11'&&!doctors.some(d=>d.isActive&&d.assignedRoomId===r.id));assert.ok(room,'Need unused actual ROOM-11 for this dedicated dataset');fixture.room=room;await save();}
for(const [key,client] of [['doctor_a',a],['doctor_b',b],['doctor_c',c]])if(!doctors.some(d=>d.userId===client.tokens.user.id))await call(admin,'/api/directory/doctors',{method:'POST',body:{userId:client.tokens.user.id,fullName:'BS E2E '+key.at(-1).toUpperCase(),title:'BS',departmentCode:fixture.room.departmentCode,assignedRoomId:fixture.room.id,specialization:'Bộ kiểm thử hướng dẫn 17/10',licenseNumber:'DEMO-1710-'+key,isActive:true,primaryForBooking:key==='doctor_a'}},201);
const assignments=await call(admin,'/api/directory/staff/assignments');
for(const [client,room] of [[staff,fixture.room],[staff,rooms.find(r=>r.id==='PHARMACY-MAIN-01')],[lab,rooms.find(r=>r.id==='LAB-HEMATOLOGY-01')]])if(!assignments.some(x=>x.isActive&&x.userId===client.tokens.user.id&&x.roomId===room.id))await call(admin,'/api/directory/staff/assignments',{method:'POST',body:{userId:client.tokens.user.id,roomId:room.id,departmentCode:room.departmentCode,isActive:true}},201);
for(const [key,label] of [['no_cls','Không CLS'],['cls','Có CLS'],['handover','Bàn giao'],['cancel','Hủy lịch'],['family','Hồ sơ gia đình']]){
 const p=await account(key,'PATIENT');const existing=await p.request(`/api/patients/user/${p.tokens.user.id}`);const profile=existing.status===200?existing.json.data:await call(p,'/api/patients',{method:'POST',body:{userId:p.tokens.user.id,fullName:'E2E '+label+' 17/10',dateOfBirth:'1995-01-01',gender:'MALE',phoneNumber:'0900001710',address:'Hồ sơ giả phục vụ kiểm thử API thật'}},201);
 fixture.accounts[key].patientId=profile.id;
}
await save();console.log('Prepared dedicated actual accounts/profiles/assignments',Object.keys(fixture.accounts).length,'room',fixture.room.id);
for(const client of [seed,admin,a,b,c,staff,lab])await client.request('/api/auth/logout',{method:'POST',token:null,body:{refreshToken:client.tokens.refreshToken}});
