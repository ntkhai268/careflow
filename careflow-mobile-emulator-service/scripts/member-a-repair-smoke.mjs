import {auditFetch as fetch, proofFor} from './bound-http.mjs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {mkdir,writeFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
const base='http://localhost:3000',password='Password123@',checks=[],findings=[],sessions=[];
let browser,page;
const resources={};
function verify(name,condition,detail) { checks.push({case:name,result:condition?'PASS':'FAIL',...(detail?{detail}:{})});console.log(`${condition?'PASS':'FAIL'} ${name}`); }
function finding(id,severity,description,evidence){findings.push({id,severity,description,evidence});}
async function api(name,path,{method='GET',token,body,statuses=[200],headers={},direct}={}) {
  const response=await fetch((direct || base)+path,{method,headers:{Origin:base,...(token?{Authorization:`Bearer ${token}`} : {}),...(body?{'Content-Type':'application/json'}:{}),...headers},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(20000)});
  const raw=await response.text();let payload;
  try{payload=JSON.parse(raw);}catch{payload={};}
  const ok=statuses.includes(response.status);
  checks.push({case:name,result:ok?'PASS':'FAIL',method,path,status:response.status});
  if(!ok)throw new Error(`${name}: expected ${statuses.join('/')}, actual HTTP ${response.status}`);
  return path.startsWith('/__emulator/')?payload:payload.data;
}
async function login(username) {
  const auth=await api('login-'+username,'/api/auth/login',{method:'POST',body:{usernameOrEmail:username,password}});
  sessions.push(auth.refreshToken);return auth;
}
async function patient(label) {
  const username='auditA'+label+randomUUID().replaceAll('-','').slice(0,10);
  const user=await api('register-'+label,'/api/auth/register',{method:'POST',statuses:[201],body:{username,email:`${username}@example.test`,password,role:'ADMIN'}});
  assert.equal(user.role,'PATIENT');
  const auth=await login(username);
  const profile=await api('profile-'+label,'/api/patients',{method:'POST',statuses:[201],token:auth.accessToken,body:{userId:user.id,fullName:`Audit A ${label}`,gender:'OTHER'}});
  return {user,auth,profile,username};
}
async function waitInbox(token,predicate) {
  for(let attempt=0;attempt<40;attempt++){
    const result=await api('poll-real-inbox','/api/notifications?limit=100',{token});
    if(predicate(result))return result;
    await new Promise(resolve=>setTimeout(resolve,200));
  }
  return api('final-real-inbox','/api/notifications?limit=100',{token});
}
async function connect(id,token,destination='/user/queue/notifications') {
  const dpop=token?proofFor(token,'GET','/ws/notifications'):null;
  return page.evaluate(({id,token,destination,dpop})=>new Promise(resolve=>{
    window.auditSockets ||= {};
    const socket=new WebSocket(`ws://${location.host}/ws/notifications`),state={socket,messages:[],frames:[],closed:false};window.auditSockets[id]=state;
    let settled=false,buffer='';
    const done=value=>{if(!settled){settled=true;clearTimeout(timer);resolve(value);}};
    const timer=setTimeout(()=>done('TIMEOUT'),3500);
    socket.onopen=()=>socket.send(`CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n${token?`Authorization:Bearer ${token}\nDPoP:${dpop}\n`:''}\n\0`);
    socket.onmessage=event=>{
      buffer+=event.data;let end;
      while((end=buffer.indexOf('\0'))>=0){
        const frame=buffer.slice(0,end).replace(/^\n+/, '');buffer=buffer.slice(end+1);const command=frame.split('\n',1)[0];state.frames.push(command);
        if(command==='CONNECTED'){
          if(destination)socket.send(`SUBSCRIBE\nid:${id}\ndestination:${destination}\nack:auto\n\n\0`);
          done('CONNECTED');
        }
        if(command==='MESSAGE'){try{state.messages.push(JSON.parse(frame.slice(frame.indexOf('\n\n')+2)));}catch{}}
        if(command==='ERROR')done('ERROR');
      }
    };
    socket.onclose=()=>{state.closed=true;done('CLOSED');};socket.onerror=()=>done('ERROR');
  }),{id,token,destination,dpop});
}
async function messages(id){return page.evaluate(id=>window.auditSockets[id].messages,id);}
const {execFileSync}=await import('node:child_process');
const uuid=value=>{assert.match(value,/^[0-9a-f-]{36}$/i);return value;};
function sql(database,statement){return execFileSync('docker',['exec','careflow-dangkhoii-auth-audit-postgres','psql','-U','careflow_audit','-d',database,'-v','ON_ERROR_STOP=1','-Atc',statement],{encoding:'utf8'}).trim();}
const artifacts={createdPatients:[],queueEntries:[],appointments:[],wsCases:[]};
async function requireClosed(id){
  for(let attempt=0;attempt<80;attempt++){
    if(await page.evaluate(id=>window.auditSockets[id].closed,id))return;
    await page.waitForTimeout(100);
  }
  throw Error(`Socket ${id} remains open after revocation/expiry`);
}
try {
  const config=await api('audit-config','/__emulator/config');assert.equal(config.gateway,'https://localhost:18080');
  const admin=await login('admin'),doctor=await login('an@careflow.vn');
  const owner=await patient('repairOwner'),socketUser=await patient('socket');
  const staffAccount=await patient('staff'),unassignedAccount=await patient('unassigned');
  for(const account of [staffAccount,unassignedAccount]){
    await api('assign-real-STAFF-role','/api/users/'+account.user.id+'/role',{method:'PATCH',token:admin.accessToken,body:{role:'STAFF'}});
    account.auth=await login(account.username);
  }
  const assignment=await api('create-actual-reception-assignment','/api/directory/staff/assignments',{method:'POST',statuses:[201],token:admin.accessToken,body:{userId:staffAccount.user.id,roomId:'ROOM-01',departmentCode:'NOI_TONG_QUAT',isActive:true}});
  artifacts.assignmentId=assignment.id;artifacts.staffUserId=staffAccount.user.id;artifacts.ownerUserId=owner.user.id;
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
  page=await (await browser.newContext()).newPage();await page.goto(base+'/__emulator/config');
  verify('active-personal-websocket-connect',await connect('active',owner.auth.accessToken)==='CONNECTED');
  verify('no-token-websocket-denied',await connect('anonymous',null)!=='CONNECTED');
  await connect('foreign',owner.auth.accessToken,`/user/${staffAccount.user.id}/queue/notifications`);await requireClosed('foreign');
  verify('foreign-destination-is-closed',true);
  for(const action of ['logout','logout-all','refresh','disable','role-change']){
    const auth=await login(socketUser.username),id='revoke-'+action;
    assert.equal(await connect(id,auth.accessToken),'CONNECTED');
    if(action==='logout')await api('real-logout-current','/api/auth/logout',{method:'POST',body:{refreshToken:auth.refreshToken}});
    if(action==='logout-all')await api('real-logout-all','/api/auth/logout-all',{method:'POST',token:auth.accessToken});
    if(action==='refresh'){
      const refreshed=await api('real-refresh-rotation','/api/auth/refresh',{method:'POST',body:{refreshToken:auth.refreshToken}});sessions.push(refreshed.refreshToken);
    }
    if(action==='disable')await api('real-admin-disable','/api/users/'+socketUser.user.id+'/status',{method:'PATCH',token:admin.accessToken,body:{status:'DISABLED'}});
    if(action==='role-change')await api('real-admin-change-role','/api/users/'+socketUser.user.id+'/role',{method:'PATCH',token:admin.accessToken,body:{role:'STAFF'}});
    await requireClosed(id);
    await api(`${action}-old-token-REST-denied`,'/api/notifications',{token:auth.accessToken,statuses:[401]});
    assert.notEqual(await connect(id+'-retry',auth.accessToken),'CONNECTED');
    verify(`${action}-closes-idle-socket-and-rejects-reconnect`,true);artifacts.wsCases.push(action);
    if(action==='disable')await api('restore-fixture-user-active','/api/users/'+socketUser.user.id+'/status',{method:'PATCH',token:admin.accessToken,body:{status:'ACTIVE'}});
    if(action==='role-change')await api('restore-fixture-user-role','/api/users/'+socketUser.user.id+'/role',{method:'PATCH',token:admin.accessToken,body:{role:'PATIENT'}});
  }
  const short=await api('real-short-lived-Identity-login','/api/auth/login',{method:'POST',direct:'https://localhost:18093',body:{usernameOrEmail:socketUser.username,password}});sessions.push(short.refreshToken);
  const claims=JSON.parse(Buffer.from(short.accessToken.split('.')[1],'base64url'));
  assert.ok(claims.exp-claims.iat<=5,'Token must be issued with a real short lifetime.');
  assert.equal(await connect('expiry',short.accessToken),'CONNECTED');await requireClosed('expiry');
  verify('actual-issued-token-expiry-closes-idle-socket',true);artifacts.wsCases.push('expiry');
  const token=owner.auth.accessToken,staff=staffAccount.auth.accessToken,unassigned=unassignedAccount.auth.accessToken;
  const departmentId='de000001-0000-0000-0000-000000000001';
  const intake={patientId:owner.profile.id,userId:owner.user.id,departmentId,priorityLevel:'WALK_IN'};
  const before=Number(sql('careflow_queue',`select count(*) from queue.queue_entries where patient_id='${uuid(owner.profile.id)}'`));
  await api('unassigned-STAFF-intake-denied','/api/queues/entries',{method:'POST',token:unassigned,body:intake,headers:{'Idempotency-Key':randomUUID()},statuses:[403]});
  await api('forged-owner-intake-denied','/api/queues/entries',{method:'POST',token:staff,body:{...intake,userId:staffAccount.user.id},headers:{'Idempotency-Key':randomUUID()},statuses:[403]});
  await api('reception-STAFF-cannot-self-assign-emergency-priority','/api/queues/entries',{method:'POST',token:staff,body:{...intake,priorityLevel:'EMERGENCY'},headers:{'Idempotency-Key':randomUUID()},statuses:[403]});
  await api('nonexistent-patient-intake-denied','/api/queues/entries',{method:'POST',token:staff,body:{...intake,patientId:randomUUID()},headers:{'Idempotency-Key':randomUUID()},statuses:[404]});
  assert.equal(Number(sql('careflow_queue',`select count(*) from queue.queue_entries where patient_id='${uuid(owner.profile.id)}'`)),before);
  verify('denied-intakes-create-no-entry',true);
  const key=randomUUID();
  const entry=await api('assigned-STAFF-real-default-ROOM01-intake','/api/queues/entries',{method:'POST',token:staff,body:intake,headers:{'Idempotency-Key':key},statuses:[201]});
  artifacts.queueEntries.push(entry.entryId);assert.equal(entry.queueStatus,'CHECKED_IN');assert.ok(Number(entry.queueNumber.split('-').at(-1))>5);
  verify('seeded-number-collision-fixed-without-deleting-seed',true);
  const replays=await Promise.all(Array.from({length:4},(_,index)=>api('parallel-same-key-replay-two-JVMs','/api/queues/entries',{method:'POST',token:staff,body:intake,headers:{'Idempotency-Key':key},statuses:[201],...(index%2 ? {direct:'https://localhost:18094'} : {})})));
  assert.ok(replays.every(item=>item.entryId===entry.entryId));
  assert.equal(sql('careflow_queue',`select count(*) from queue.outbox_events where aggregate_id='${uuid(entry.entryId)}' and event_type='PatientCheckedIn'`),'1');
  verify('concurrent-replay-one-entry-one-checkin-event',true);
  const actor=sql('careflow_queue',`select checked_in_by_user_id,check_in_method,user_id from queue.queue_entries where id='${uuid(entry.entryId)}'`).split('|');
  assert.deepEqual(actor,[staffAccount.user.id,'STAFF_MANUAL_INTAKE',owner.user.id]);verify('creator-and-patient-owner-separated',true);
  await api('duplicate-active-intake-denied','/api/queues/entries',{method:'POST',token:staff,body:intake,headers:{'Idempotency-Key':randomUUID()},statuses:[409]});
  for(let attempt=0;attempt<50 && !(await messages('active')).some(item=>item.action?.resourceId===entry.entryId);attempt++)await page.waitForTimeout(100);
  assert.ok((await messages('active')).some(item=>item.type==='CHECK_IN_SUCCESS' && item.action?.resourceId===entry.entryId));verify('active-session-still-receives-real-notification',true);
  const callPath='/api/queues/entries/'+entry.entryId;
  await api('assigned-doctor-call-intake',callPath+'/call',{method:'POST',token:doctor.accessToken,headers:{'Idempotency-Key':randomUUID()}});
  await api('assigned-doctor-start-intake',callPath+'/start',{method:'POST',token:doctor.accessToken});
  await api('assigned-doctor-complete-intake',callPath+'/complete',{method:'POST',token:doctor.accessToken});
  const noAccountRequest={roomId:'ROOM-01',fullName:'Real no-account reception',dateOfBirth:'1950-01-01',gender:'OTHER',idCardNumber:'A'+randomUUID().replaceAll('-','').slice(0,15)};
  await api('PATIENT-cannot-create-unowned-profile','/api/patients/reception',{method:'POST',token,body:noAccountRequest,statuses:[403]});
  await api('unassigned-STAFF-cannot-create-profile','/api/patients/reception',{method:'POST',token:unassigned,body:noAccountRequest,statuses:[403]});
  await api('STAFF-wrong-room-profile-denied','/api/patients/reception',{method:'POST',token:staff,body:{...noAccountRequest,roomId:'ROOM-02'},statuses:[403]});
  await api('unknown-room-profile-denied','/api/patients/reception',{method:'POST',token:admin.accessToken,body:{...noAccountRequest,roomId:'ROOM-MISSING'},statuses:[403]});
  const offline=await api('assigned-STAFF-creates-real-no-account-patient','/api/patients/reception',{method:'POST',token:staff,body:noAccountRequest,statuses:[201]});
  assert.equal(offline.userId,null);artifacts.createdPatients.push(offline.id);
  await api('duplicate-unowned-ID-card-denied','/api/patients/reception',{method:'POST',token:staff,body:noAccountRequest,statuses:[409]});
  const offlineBody={patientId:offline.id,departmentId,priorityLevel:'WALK_IN'};
  const offlineEntry=await api('assigned-STAFF-intakes-patient-without-login','/api/queues/entries',{method:'POST',token:staff,body:offlineBody,headers:{'Idempotency-Key':randomUUID()},statuses:[201]});
  assert.equal(offlineEntry.userId,null);assert.equal(offlineEntry.appointmentId,null);assert.equal(offlineEntry.queueStatus,'CHECKED_IN');artifacts.queueEntries.push(offlineEntry.entryId);
  const audit=sql('careflow_patient',`select coalesce(user_id::text,'NULL'),reception_created_by_user_id,reception_room_id from patients where id='${uuid(offline.id)}'`).split('|');
  assert.deepEqual(audit,['NULL',staffAccount.user.id,'ROOM-01']);verify('no-account-patient-stays-unowned-with-audit',true);
  await api('creator-is-not-clinical-owner','/api/patients/'+offline.id,{token:staff,statuses:[403]});
  await api('unrelated-STAFF-cannot-edit-unowned-profile','/api/patients/'+offline.id,{method:'PUT',token:unassigned,body:{fullName:'Unauthorized change'},statuses:[403]});
  const extraProfiles=[];
  for(let number=0;number<4;number++){
    const profile=await api('real-parallel-intake-profile','/api/patients',{method:'POST',token,statuses:[201],body:{userId:owner.user.id,fullName:`Real concurrent intake ${number}`,gender:'OTHER'}});
    extraProfiles.push(profile);artifacts.createdPatients.push(profile.id);
  }
  const parallelEntries=await Promise.all(extraProfiles.map((profile,index)=>api('concurrent-distinct-patient-intake-two-JVMs','/api/queues/entries',{method:'POST',token:staff,body:{...intake,patientId:profile.id},headers:{'Idempotency-Key':randomUUID()},statuses:[201],...(index%2 ? {direct:'https://localhost:18094'} : {})})));
  artifacts.queueEntries.push(...parallelEntries.map(item=>item.entryId));assert.equal(new Set(parallelEntries.map(item=>item.queueNumber)).size,4);verify('concurrent-actual-allocations-use-distinct-numbers',true);
  const contender=await api('profile-for-duplicate-race','/api/patients',{method:'POST',token,statuses:[201],body:{userId:owner.user.id,fullName:'Real duplicate race',gender:'OTHER'}});
  const raced=await Promise.all([base,'https://localhost:18094'].map(origin=>fetch(origin+'/api/queues/entries',{method:'POST',headers:{Origin:base,'Content-Type':'application/json',Authorization:`Bearer ${staff}`,'Idempotency-Key':randomUUID()},body:JSON.stringify({...intake,patientId:contender.id})})));
  assert.deepEqual(raced.map(item=>item.status).sort(),[201,409]);for(const response of raced)await response.arrayBuffer();verify('different-key-concurrent-intake-cannot-duplicate-patient',true);
  const bookingProfile=await api('fresh-second-profile-for-concurrent-appointment-queue-events','/api/patients',{method:'POST',token,statuses:[201],body:{userId:owner.user.id,fullName:'Real new booking profile',gender:'OTHER'}});
  const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(Date.now()+86400000));
  const slots=await api('real-booking-slots',`/api/appointments/time-slots/availability?department=NOI_TONG_QUAT&date=${date}`,{token});const slot=slots.find(item=>item.available && item.remaining>0);assert.ok(slot);
  const booking=await api('real-second-profile-booking','/api/appointments',{method:'POST',token,statuses:[201],headers:{'Idempotency-Key':randomUUID()},body:{patientId:bookingProfile.id,patientName:bookingProfile.fullName,department:'NOI_TONG_QUAT',appointmentDate:date,timeSlot:slot.timeSlot,reason:'Real member A repair verification'}});artifacts.appointments.push(booking.id);
  const inbox=await waitInbox(token,items=>items.some(item=>item.type==='APPOINTMENT_CONFIRMED' && item.action?.resourceId===booking.id));
  assert.ok(inbox.some(item=>item.type==='APPOINTMENT_CONFIRMED' && item.action?.resourceId===booking.id));verify('second-profile-actual-booking-notification-fixed',true);
  assert.ok(Number(sql('careflow_notification_audit',`select count(*) from notification.patient_recipients where user_id='${uuid(owner.user.id)}'`))>=2);verify('multiple-real-patient-mappings-per-user',true);
  artifacts.ownerProfileIds=[owner.profile.id,bookingProfile.id];
  await api('cancel-only-audit-booking','/api/appointments/'+booking.id+'/cancel',{method:'PUT',token});
  const assignmentPath='/api/directory/staff/assignments/'+assignment.id;
  await api('deactivate-test-reception-assignment',assignmentPath,{method:'PUT',token:admin.accessToken,body:{userId:staffAccount.user.id,roomId:'ROOM-01',departmentCode:'NOI_TONG_QUAT',isActive:false}});
  await api('inactive-assignment-denies-existing-idempotency-replay','/api/queues/entries',{method:'POST',token:staff,body:intake,headers:{'Idempotency-Key':key},statuses:[403]});
  await api('inactive-assignment-cannot-create-patient','/api/patients/reception',{method:'POST',token:staff,body:{...noAccountRequest,idCardNumber:null},statuses:[403]});
  verify('all-four-defect-regressions-fixed',true);
} catch(error) {
  findings.push({id:'REPAIR-REGRESSION-FAILURE',description:error.message});console.error(error.stack);process.exitCode=1;
} finally {
  if(page)await page.evaluate(()=>{for(const state of Object.values(window.auditSockets || {}))state.socket.close();}).catch(()=>{});
  if(browser)await browser.close();
  for(const refreshToken of sessions)await api('cleanup-audit-session','/api/auth/logout',{method:'POST',body:{refreshToken}}).catch(()=>{});
  await mkdir(new URL('../.runtime/',import.meta.url),{recursive:true});
  const result={checkedAt:new Date().toISOString(),mode:'real',mock:false,gateway:'https://localhost:18080',passed:checks.filter(item=>item.result==='PASS').length,failed:checks.filter(item=>item.result==='FAIL').length,checks,findings,artifacts};
  await writeFile(new URL('../.runtime/member-a-repair-results.json',import.meta.url),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({passed:result.passed,failed:result.failed,findings}));
  if(result.failed || findings.length)process.exitCode=1;
}
