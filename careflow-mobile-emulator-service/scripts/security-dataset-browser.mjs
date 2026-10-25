import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
let playwright;
try { playwright=require('playwright'); }
catch { playwright=require(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package'); }
const dataset=JSON.parse(await readFile(new URL('../.runtime/mobile-dataset.json',import.meta.url),'utf8'));
assert.ok(['http://127.0.0.1:18180','https://localhost:18080'].includes(dataset.gateway));
const config=await (await fetch('http://localhost:3000/__emulator/config')).json();
assert.equal(config.gateway,dataset.gateway);
const browser=await playwright.chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const context=await browser.newContext({viewport:{width:1700,height:1250}});
const page=await context.newPage();page.setDefaultTimeout(30000);
const sessions=[],requests=[],cases=[],errors=[],profiles=new Map();
page.on('pageerror',error=>errors.push(error.message));
page.on('response',async response=>{
  const request=response.request(),path=new URL(response.url()).pathname;
  if (!path.startsWith('/api/')) return;
  requests.push({method:request.method(),path,status:response.status(),device:request.headers()['x-emulator-device']});
  if (path==='/api/auth/login' && response.status()===200) {
    const payload=await response.json(); sessions.push({refreshToken:payload.data.refreshToken,device:request.headers()['x-emulator-device']});
  }
  if (/\/api\/patients\/user\/[^/]+$/.test(path) && response.status()===200) profiles.set(request.headers()['x-emulator-device'],(await response.json()).data);
});
await page.addInitScript(()=>{
  const params=new URLSearchParams(location.search),device=params.get('device'),scope=params.get('scope');
  if (device && scope) localStorage.setItem(`careflow.${scope}.${device}.preferences.has_seen_onboarding`,'true');
});
function passed(name){cases.push({case:name,result:'PASS'});console.log(`PASS ${name}`);}
function frame(id){return page.frames().find(item=>new URL(item.url()).searchParams.get('device')===id);}
async function semantics(child){const placeholder=child.locator('flt-semantics-placeholder');if(await placeholder.count())await placeholder.evaluate(el=>el.click());}
try {
  await page.goto('http://localhost:3000/');
  for (const scenario of dataset.scenarios) {
    const id=scenario.deviceId,card=page.locator(`[data-device-id="${id}"]`);
    await page.waitForFunction(device=>{const status=document.querySelector(`[data-device-id="${device}"] .device-session`);return status && !status.textContent.includes('Đang mở');},id);
    await card.locator('.device-scenario').evaluate(element=>{if(element.tagName==='DETAILS')element.open=true;});
    assert.ok((await card.locator('.scenario-credentials').innerText()).includes(scenario.username));
    const child=frame(id);assert.equal(new URL(child.url()).searchParams.get('demo'),'false');
    await semantics(child);
    const username=child.locator('input[aria-label="Nhập tên đăng nhập hoặc email"]');
    await username.click();await page.waitForTimeout(200);
    await child.locator('input:focus').fill(scenario.username);
    const password=child.locator('input[aria-label="Nhập mật khẩu"]');
    await password.click();await page.waitForTimeout(200);
    await child.locator('input:focus').fill(scenario.password);
    await page.waitForTimeout(200);
    const [loginResponse]=await Promise.all([
      page.waitForResponse(response=>new URL(response.url()).pathname==='/api/auth/login' && response.request().headers()['x-emulator-device']===id),
      child.getByRole('button',{name:'Đăng nhập',exact:true}).click(),
    ]);
    const submitted=loginResponse.request().postDataJSON();
    assert.ok(submitted.usernameOrEmail===scenario.username && submitted.password===scenario.password,'Automation must submit the exact dataset credentials.');
    assert.equal(loginResponse.status(),200,'Real login must succeed.');
    await page.waitForFunction(device=>document.querySelector(`[data-device-id="${device}"] .device-session`).classList.contains('authenticated'),id);
    for (let attempt=0;attempt<100 && !profiles.has(id);attempt++) await page.waitForTimeout(100);
    assert.equal(profiles.get(id)?.id,scenario.patientId);
    passed(`${id}: actual login and owned profile for ${scenario.username}`);
  }
  await page.locator('[data-command=appointments]').click();
  for (const scenario of dataset.scenarios) {
    const child=frame(scenario.deviceId);
    await page.waitForFunction(device=>document.querySelector(`[data-device-id="${device}"] iframe`).contentWindow.location.hash.includes('tab=1'),scenario.deviceId);
    await semantics(child);
    const expected=scenario.expectedStatus==='CANCELLED' ? 'Đã hủy' : scenario.expectedStatus==='CONFIRMED' ? 'Đã xác nhận' : 'Chưa có lịch khám sắp tới';
    if (scenario.expectedStatus) await child.getByRole('button',{name:new RegExp(`${scenario.fullName}.*${expected}`,'s')}).waitFor({state:'attached'});
    else await child.getByText(expected,{exact:true}).waitFor({state:'attached'});
    assert.ok(requests.some(item=>item.path===`/api/appointments/patient/${scenario.patientId}` && item.device===scenario.deviceId && item.status===200));
    assert.ok(!requests.some(item=>item.device===scenario.deviceId && dataset.scenarios.some(other=>other.deviceId!==scenario.deviceId && item.path===`/api/appointments/patient/${other.patientId}`)));
    passed(`${scenario.deviceId}: real appointment screen shows ${expected}; no other patient's appointment request`);
  }
  await page.screenshot({path:new URL('../.runtime/dataset-real-phones.png',import.meta.url).pathname,fullPage:true});
  assert.deepEqual(errors,[]);passed('three real sessions simultaneously render without uncaught JavaScript errors');
  await writeFile(new URL('../.runtime/dataset-browser-results.json',import.meta.url),JSON.stringify({gateway:dataset.gateway,mode:'real',mock:false,cases,requests,errors},null,2)+'\n');
} catch(error) {
  await page.screenshot({path:new URL('../.runtime/dataset-browser-failure.png',import.meta.url).pathname,fullPage:true});
  await writeFile(new URL('../.runtime/dataset-browser-failure.json',import.meta.url),JSON.stringify({error:error.message,cases,errors,requests,screens:await Promise.all(page.frames().filter(item=>item.url().includes('/mobile/')).map(async item=>({url:item.url(),text:await item.locator('body').innerText()})))},null,2));
  throw error;
} finally {
  try {
    for (const {refreshToken,device} of sessions) {
      const status=await frame(device).evaluate(async refreshToken=>{
        const proof=await window.careflowProof({method:'POST',path:'/api/auth/logout'});
        return (await fetch('/api/auth/logout',{method:'POST',headers:{'content-type':'application/json',DPoP:proof},body:JSON.stringify({refreshToken})})).status;
      },refreshToken);
      assert.equal(status,200,'Real dataset session cleanup must revoke its bound session.');
    }
  } finally { await browser.close(); }
}
