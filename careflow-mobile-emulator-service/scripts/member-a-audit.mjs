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
  return page.evaluate(({id,token,destination})=>new Promise(resolve=>{
    window.auditSockets ||= {};
    const socket=new WebSocket(`ws://${location.host}/ws/notifications`),state={socket,messages:[],frames:[],closed:false};window.auditSockets[id]=state;
    let settled=false,buffer='';
    const done=value=>{if(!settled){settled=true;clearTimeout(timer);resolve(value);}};
    const timer=setTimeout(()=>done('TIMEOUT'),3500);
    socket.onopen=()=>socket.send(`CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n${token?`Authorization:Bearer ${token}\n`:''}\n\0`);
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
  }),{id,token,destination});
}
async function messages(id){return page.evaluate(id=>window.auditSockets[id].messages,id);}
try {
  const config=await api('simulator-config','/__emulator/config');assert.equal(config.gateway,'http://127.0.0.1:18080');
  for(const [name,port] of [['gateway',18080],['identity',18081],['queue',18084],['notification',18085],['eureka',18761]]){
    const response=await fetch(`http://127.0.0.1:${port}/actuator/health`);verify(`health-${name}`,response.status===200);
  }
  await api('gateway-requires-auth','/api/notifications',{statuses:[401]});
  await api('direct-notification-rejects-forged-identity','/api/notifications',{direct:'http://127.0.0.1:18085',headers:{'X-User-Id':randomUUID(),'X-User-Role':'ADMIN'},statuses:[401]});
  const owner=await patient('owner'),other=await patient('other');resources.ownerUserId=owner.user.id;resources.otherUserId=other.user.id;
  const admin=await login('admin'),doctor=await login('an@careflow.vn'),wrongDoctor=await login('doctor'),staff=await login('staff');
  const token=owner.auth.accessToken;
  await api('cross-user-profile-denied',`/api/patients/user/${owner.user.id}/profiles`,{token:other.auth.accessToken,statuses:[403]});
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
  page=await (await browser.newContext()).newPage();await page.goto(base+'/__emulator/config');
  verify('websocket-valid-connect',await connect('active',token)==='CONNECTED');
  verify('websocket-no-token-denied',await connect('anonymous',null)!=='CONNECTED');
  await connect('foreign',token,`/user/${other.user.id}/queue/notifications`);await page.waitForTimeout(250);
  verify('websocket-foreign-destination-denied',await page.evaluate(()=>window.auditSockets.foreign.frames.includes('ERROR') || window.auditSockets.foreign.closed));
  const revoked=await login(owner.username);
  verify('websocket-second-session-before-logout',await connect('beforeLogout',revoked.accessToken)==='CONNECTED');
  await api('revoke-websocket-test-session','/api/auth/logout',{method:'POST',body:{refreshToken:revoked.refreshToken}});
  await api('revoked-session-http-denied','/api/notifications',{token:revoked.accessToken,statuses:[401]});
  await api('revoked-session-direct-notification-denied','/api/notifications',{direct:'http://127.0.0.1:18085',token:revoked.accessToken,statuses:[401]});
  const revokedConnect=await connect('afterLogout',revoked.accessToken);
  verify('revoked-session-websocket-denied',revokedConnect!=='CONNECTED',{actual:revokedConnect});
  if(revokedConnect==='CONNECTED')finding('A-NOT-SESSION','HIGH','WebSocket accepts an access token after logout, while Gateway/direct REST reject it.',{handshake:revokedConnect,httpStatus:401});
  const intakeBody={patientId:owner.profile.id,userId:owner.user.id,departmentId:'de000001-0000-0000-0000-000000000001',priorityLevel:'WALK_IN'};
  await api('staff-manual-intake-currently-denied','/api/queues/entries',{method:'POST',token:staff.accessToken,body:intakeBody,headers:{'Idempotency-Key':randomUUID()},statuses:[403]});
  finding('A-QUEUE-STAFF','HIGH','Reception walk-in is ADMIN-only; STAFF cannot use manual intake and a patient without a user account cannot be represented.',{staffStatus:403,requiredUserId:true});
  const key=randomUUID();
  let entry=await api('admin-default-room-walk-in','/api/queues/entries',{method:'POST',token:admin.accessToken,body:intakeBody,headers:{'Idempotency-Key':key},statuses:[201,500]});
  let operator=doctor.accessToken;
  if(!entry?.entryId){
    verify('default-seeded-room-can-create-intake',false,{httpStatus:500});
    finding('A-QUEUE-SEED-SEQUENCE','HIGH','Default seeded ROOM-01 manual intake returns HTTP 500: next NOI-001 duplicates seeded queue number; transaction rolls back without advancing the sequence.',{status:500,constraint:'uk_queue_number_per_day'});
    const departments=await api('actual-directory-for-isolated-queue','/api/directory/departments');
    const department=departments.find(item=>item.code==='NHI');assert.ok(department);
    const rooms=await api('actual-rooms-for-isolated-queue','/api/directory/rooms');
    const room=rooms.find(item=>item.departmentCode==='NHI' && item.roomType==='CONSULTATION' && item.isActive);assert.ok(room);
    await api('configure-real-audit-queue-for-existing-NHI',`/api/queues/configs/${department.id}`,{method:'PUT',token:admin.accessToken,body:{departmentName:department.name,queuePrefix:'AUDNHI',roomCode:room.id,priorityRatioN:1,normalRatioM:1,avgConsultationMinutes:15,nearTurnThreshold:3,missedPolicy:'REQUEUE_BACK',active:true}});
    intakeBody.departmentId=department.id;operator=admin.accessToken;
    entry=await api('admin-real-walk-in-isolated-department','/api/queues/entries',{method:'POST',token:admin.accessToken,body:intakeBody,headers:{'Idempotency-Key':key},statuses:[201]});
    resources.queueCommandActor='ADMIN';resources.queueDepartment='NHI';
  }
  resources.queueEntryId=entry.entryId;
  verify('intake-checked-in',entry.queueStatus==='CHECKED_IN');
  const replay=await api('manual-intake-idempotent-replay','/api/queues/entries',{method:'POST',token:admin.accessToken,body:intakeBody,headers:{'Idempotency-Key':key},statuses:[201]});verify('manual-intake-same-entry',replay.entryId===entry.entryId);
  const currentPath=`/api/queues/patients/${owner.profile.id}/current`;
  const current=await api('patient-own-current-queue',currentPath,{token});verify('current-queue-owned',current.entryId===entry.entryId && current.userId===owner.user.id);
  await api('other-patient-current-queue-denied',currentPath,{token:other.auth.accessToken,statuses:[403,404]});
  await api('wrong-doctor-call-denied',`/api/queues/entries/${entry.entryId}/call`,{method:'POST',token:wrongDoctor.accessToken,headers:{'Idempotency-Key':randomUUID()},statuses:[403]});
  const untouched=await api('denied-call-state-readback',currentPath,{token});verify('denied-call-no-state-change',untouched.queueStatus==='CHECKED_IN' && untouched.callAttempts===0);
  await waitInbox(token,items=>items.some(item=>item.type==='CHECK_IN_SUCCESS' && item.action?.resourceId===entry.entryId));
  for(let attempt=0;attempt<50 && !(await messages('active')).some(item=>item.type==='CHECK_IN_SUCCESS');attempt++)await page.waitForTimeout(100);
  verify('queue-event-real-websocket-message',(await messages('active')).some(item=>item.type==='CHECK_IN_SUCCESS' && item.action?.resourceId===entry.entryId));
  const afterLogoutMessages=(await messages('beforeLogout')).filter(item=>item.action?.resourceId===entry.entryId);
  verify('existing-websocket-stops-after-logout',afterLogoutMessages.length===0,{receivedAfterLogout:afterLogoutMessages.length});
  const callKey=randomUUID(),callPath=`/api/queues/entries/${entry.entryId}`;
  const called=await api('authorized-actor-call',callPath+'/call',{method:'POST',token:operator,headers:{'Idempotency-Key':callKey}});verify('queue-called',called.queueStatus==='CALLED');
  await api('call-idempotent-replay',callPath+'/call',{method:'POST',token:operator,headers:{'Idempotency-Key':callKey}});
  for(let count=2;count<=3;count++){
    const recalled=await api('recall-'+count,callPath+'/recall',{method:'POST',token:operator});verify('recall-attempt-'+count,recalled.callAttempts===count);
  }
  await api('fourth-call-rejected',callPath+'/recall',{method:'POST',token:operator,statuses:[409]});
  await api('mark-missed',callPath+'/miss',{method:'POST',token:operator});
  const requeued=await api('requeue-back',callPath+'/requeue',{method:'POST',token:operator,body:{position:'BACK'}});verify('requeued-check-in',requeued.queueStatus==='CHECKED_IN');
  await api('call-after-requeue',callPath+'/call',{method:'POST',token:operator,headers:{'Idempotency-Key':randomUUID()}});
  const started=await api('start-real-queue-entry',callPath+'/start',{method:'POST',token:operator});verify('queue-started',started.queueStatus==='IN_PROGRESS');
  const completed=await api('complete-real-queue-entry',callPath+'/complete',{method:'POST',token:operator});verify('queue-completed',completed.queueStatus==='COMPLETED');
  await api('duplicate-complete-denied',callPath+'/complete',{method:'POST',token:operator,statuses:[409]});
  await api('no-account-intake-not-supported','/api/queues/entries',{method:'POST',token:admin.accessToken,body:{...intakeBody,userId:null},headers:{'Idempotency-Key':randomUUID()},statuses:[400]});
  let inbox=await waitInbox(token,items=>items.some(item=>item.type==='QUEUE_CALLED'));
  const item=inbox.find(item=>item.type==='QUEUE_CALLED');assert.ok(item,'Called event must produce persisted inbox notification');resources.notificationId=item.id;
  await api('foreign-inbox-read-denied',`/api/notifications/${item.id}/read`,{method:'POST',token:other.auth.accessToken,statuses:[404]});
  const unread=await api('unread-count','/api/notifications/unread-count',{token});verify('unread-count-positive',unread.unreadCount>0);
  const read=await api('mark-owned-notification-read',`/api/notifications/${item.id}/read`,{method:'POST',token});verify('read-state',read.status==='READ');
  await api('mark-read-idempotent',`/api/notifications/${item.id}/read`,{method:'POST',token});
  await api('mark-all-read','/api/notifications/read-all',{method:'POST',token});
  const noneUnread=await api('unread-after-read-all','/api/notifications/unread-count',{token});verify('all-read-zero',noneUnread.unreadCount===0);
  const secondProfile=await api('second-real-patient-profile','/api/patients',{method:'POST',statuses:[201],token,body:{userId:owner.user.id,fullName:'Audit A second profile',gender:'OTHER'}});resources.secondPatientId=secondProfile.id;
  const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(Date.now()+86400000));
  const slots=await api('booking-real-capacity',`/api/appointments/time-slots/availability?department=NOI_TONG_QUAT&date=${date}`,{token});const slot=slots.find(item=>item.available && item.remaining>0);assert.ok(slot);
  const booking=await api('second-profile-real-booking','/api/appointments',{method:'POST',statuses:[201],token,headers:{'Idempotency-Key':randomUUID()},body:{patientId:secondProfile.id,patientName:secondProfile.fullName,department:'NOI_TONG_QUAT',appointmentDate:date,timeSlot:slot.timeSlot,reason:'Real member A audit - multi-profile notification'}});resources.appointmentId=booking.id;
  for(let attempt=0;attempt<50;attempt++){
    const ticket=await api('real-second-profile-ticket',`/api/queues/tickets/appointment/${booking.id}`,{token,statuses:[200,404]});
    if(ticket?.status){resources.ticketStatus=ticket.status;break;}
    await page.waitForTimeout(200);
  }
  const inboxAfter=await waitInbox(token,items=>items.some(item=>item.type==='APPOINTMENT_CONFIRMED' && item.action?.resourceId===booking.id));
  const notified=inboxAfter.some(item=>item.type==='APPOINTMENT_CONFIRMED' && item.action?.resourceId===booking.id);
  verify('multiple-profiles-get-booking-notification',notified);
  if(!notified)finding('A-NOT-MULTI-PROFILE','HIGH','Second patient profile of one account books successfully and gets a Queue ticket but receives no confirmation notification. Check recipient projection unique userId constraint.',{bookingStatus:booking.status,ticketStatus:resources.ticketStatus,notification:false});
  const qr=await api('real-hospital-qr','/api/queues/hospital-check-in-qr',{token:admin.accessToken});
  await api('check-in-invalid-qr-denied','/api/queues/check-in',{method:'POST',token,statuses:[401],body:{appointmentId:booking.id,checkInQrToken:'invalid',latitude:10.7769,longitude:106.7009,accuracyMeters:10}});
  await api('check-in-wrong-day-denied','/api/queues/check-in',{method:'POST',token,statuses:[409],body:{appointmentId:booking.id,checkInQrToken:qr.qrToken,latitude:10.7769,longitude:106.7009,accuracyMeters:10}});
  const unchanged=await api('denied-check-in-readback',`/api/queues/tickets/appointment/${booking.id}`,{token});verify('denied-checkin-no-state-change',unchanged.status==='TICKET_ISSUED');
  await api('cancel-audit-booking',`/api/appointments/${booking.id}/cancel`,{method:'PUT',token});
  finding('A-EKYC','UNIMPLEMENTED','Identity EkycService returns fixed identity fields and mock=true. No genuine identity verification is available. Positive mock response was not counted as a passed real feature.');
  finding('A-CHECKIN-UAT','NOT_VERIFIED','Successful same-day QR/geofence check-in cannot be verified tonight through a real bookable time slot. Wrong QR/day rejection and unchanged ticket are verified.');
  finding('A-PUSH','NOT_VERIFIED','Firebase is disabled in the audit runtime and no physical mobile registration token/provider credentials are supplied. Inbox and STOMP are tested, native push is not.');
} catch(error) {
  findings.push({id:'AUDIT-INTERRUPTED',severity:'ERROR',description:error.message});console.error(error.message);
} finally {
  if(page)await page.evaluate(()=>{for(const state of Object.values(window.auditSockets || {}))state.socket.close();}).catch(()=>{});
  if(browser)await browser.close();
  for(const refreshToken of sessions)await api('cleanup-own-audit-session','/api/auth/logout',{method:'POST',body:{refreshToken}}).catch(()=>{});
  await mkdir(new URL('../.runtime/',import.meta.url),{recursive:true});
  const result={checkedAt:new Date().toISOString(),gateway:'http://127.0.0.1:18080',via:base,mode:'real',mock:false,passed:checks.filter(item=>item.result==='PASS').length,failed:checks.filter(item=>item.result==='FAIL').length,checks,findings,resources};
  await writeFile(new URL('../.runtime/member-a-audit-results.json',import.meta.url),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({passed:result.passed,failed:result.failed,findings:findings.map(item=>({id:item.id,severity:item.severity}))}));
  if(result.failed>0 || findings.some(item=>item.severity==='ERROR'))process.exitCode=1;
}
