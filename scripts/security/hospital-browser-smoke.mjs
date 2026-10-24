import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
const base='http://localhost:5173',checks=[],errors=[];
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const context=await browser.newContext(),page=await context.newPage();
page.setDefaultTimeout(30000);page.on('pageerror',error=>errors.push(error.message));
function pass(name){checks.push({name,status:'PASS'});console.log('PASS '+name);}
try {
 await page.goto(base+'/login');
 await page.locator('#email').fill('doctor');await page.locator('#password').fill('Password123@');
 const [login]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/session/login'),page.locator('button[type=submit]').click()]);
 assert.equal(login.status(),200);assert.ok(login.request().headers().dpop);pass('production Hospital Web submits real bound doctor login');
 await page.waitForURL('**/dashboard/**');
 const verified=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/me');await page.reload();
 const me=await verified;assert.equal(me.status(),200);assert.ok(me.request().headers().dpop);pass('production reload verifies live session instead of trusting localStorage');
 await page.waitForURL('**/dashboard/**');
 const [logout]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/session/logout'),page.getByTitle('Đăng xuất',{exact:true}).click()]);
 assert.equal(logout.status(),200);assert.ok(logout.request().headers().dpop);await page.waitForURL('**/login');pass('production logout revokes bound session on Identity');
 assert.deepEqual(errors,[]);pass('Hospital Web has no uncaught JavaScript errors');
} finally {
 await writeFile('/tmp/careflow-security-hospital-browser.json',JSON.stringify({checkedAt:new Date().toISOString(),base,mock:false,checks,errors},null,2)+'\n');
 await browser.close();
}
