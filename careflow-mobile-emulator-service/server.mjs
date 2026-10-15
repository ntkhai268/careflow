import http from 'node:http';
import https from 'node:https';
import {createHash} from 'node:crypto';
import {readFile, stat} from 'node:fs/promises';
import {resolve, extname, dirname, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
export const DEFAULT_GATEWAY = 'http://127.0.0.1:18080';
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.wasm':'application/wasm','.png':'image/png','.svg':'image/svg+xml','.ttf':'font/ttf','.otf':'font/otf','.ico':'image/x-icon','.woff2':'font/woff2'};
export function normalizeGateway(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || !['', '/', '/api', '/api/'].includes(url.pathname)) throw new Error('Nhập địa chỉ gốc của Gateway, ví dụ http://127.0.0.1:18080.');
  return url.origin;
}
export function createEmulator({gateway = process.env.CAREFLOW_GATEWAY || DEFAULT_GATEWAY, staticRoot = root} = {}) {
  let upstream = normalizeGateway(gateway);
  let nextId = 0;
  const activity = [], tunnels = new Set();
  function record(method, path, status, started, device = null) {
    // No query strings, IDs, credentials, headers or bodies in the API activity list.
    const safePath = path.split('?')[0].replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, ':id');
    activity.unshift({id: ++nextId, method, path: safePath, status, ...(device ? {device} : {}), ms: Date.now() - started, time: new Date().toISOString()});
    if (activity.length > 80) activity.pop();
  }
  function json(res, status, data) {
    res.writeHead(status, {'content-type':'application/json; charset=utf-8','cache-control':'no-store'});
    res.end(JSON.stringify(data));
  }
  function headers(req, target) {
    const result = {...req.headers, host: target.host};
    for (const key of ['x-user-id','x-user-role','x-internal-token','x-forwarded-for','x-forwarded-host','x-forwarded-proto','proxy-authorization','cookie','x-emulator-device']) delete result[key];
    // Browser Origin is retained. Gateway and Notification must authorize this
    // actual local origin; this proxy never bypasses their origin checks.
    return result;
  }
  function trustedBrowser(req) {
    if (req.headers['sec-fetch-site'] === 'cross-site') return false;
    if (!req.headers.origin) return true;
    try { return new URL(req.headers.origin).host === req.headers.host; } catch { return false; }
  }
  async function body(req) {
    let data = '';
    for await (const chunk of req) {
      data += chunk;
      if (data.length > 2048) throw new Error('Yêu cầu cấu hình quá lớn.');
    }
    return JSON.parse(data);
  }
  const server = http.createServer(async (req, res) => {
    const path = (req.url || '/').split('?')[0];
    res.setHeader('x-content-type-options','nosniff');
    res.setHeader('referrer-policy','same-origin');
    res.setHeader('x-frame-options','SAMEORIGIN');
    if (!trustedBrowser(req)) return json(res, 403, {message:'Chỉ nhận yêu cầu từ web giả lập cùng origin.'});
    if (path.startsWith('/__emulator/')) {
      if (path === '/__emulator/config' && req.method === 'GET') return json(res, 200, {gateway: upstream, storageScope: createHash('sha256').update(upstream).digest('hex').slice(0,16), origin: `http://${req.headers.host}`, source:'careflow/frontend/patient-mobile', mode:'real', built: await stat(resolve(staticRoot,'app/build/web/main.dart.js')).then(() => true).catch(() => false)});
      if (path === '/__emulator/config' && req.method === 'POST') {
        if (!req.headers['content-type']?.startsWith('application/json')) return json(res, 415, {message:'Yêu cầu JSON.'});
        try {
          upstream = normalizeGateway((await body(req)).gateway);
          for (const tunnel of tunnels) tunnel.destroy();
          activity.length = 0;
          return json(res, 200, {gateway: upstream});
        } catch (error) { return json(res, 400, {message:error.message}); }
      }
      if (path === '/__emulator/activity' && req.method === 'GET') return json(res, 200, activity);
      if (path === '/__emulator/dataset' && req.method === 'GET') {
        try {
          const dataset = JSON.parse(await readFile(resolve(staticRoot,'.runtime/mobile-dataset.json'),'utf8'));
          if (dataset.gateway !== upstream) return json(res,409,{message:'Dataset thuộc Gateway audit khác. Chuyển về Gateway của dataset để xem tài khoản.'});
          return json(res,200,dataset);
        } catch { return json(res,404,{message:'Chưa tạo dataset thật. Chạy npm run dataset:seed -- --audit-db.'}); }
      }
      if (path === '/__emulator/health' && req.method === 'GET') {
        const target = new URL('/actuator/health', upstream);
        const started = Date.now();
        const check = (target.protocol === 'https:' ? https : http).get(target, {timeout:5000}, response => {
          response.resume();
          json(res, 200, {reachable: true, status: response.statusCode, ms: Date.now()-started});
        });
        check.on('timeout', () => check.destroy(new Error('timeout')));
        check.on('error', () => { if (!res.writableEnded) json(res, 200, {reachable:false, message:'Không kết nối được Gateway. Kiểm tra địa chỉ và các service đang chạy.'}); });
        return;
      }
      return json(res, 404, {message:'Không có endpoint này.'});
    }
    if (path === '/api' || path.startsWith('/api/')) {
      const target = new URL(upstream);
      const started = Date.now();
      const suppliedDevice = req.headers['x-emulator-device'];
      const device = typeof suppliedDevice === 'string' && /^[a-z0-9-]{1,64}$/.test(suppliedDevice) ? suppliedDevice : null;
      const proxy = (target.protocol === 'https:' ? https : http).request({hostname:target.hostname, port:target.port, path:req.url, method:req.method, headers:headers(req,target), timeout:25000}, response => {
        const responseHeaders = {...response.headers, 'cache-control':'no-store'};
        delete responseHeaders['set-cookie'];
        res.writeHead(response.statusCode, responseHeaders);
        record(req.method,path,response.statusCode,started,device);
        response.pipe(res);
      });
      proxy.on('timeout', () => proxy.destroy(new Error('timeout')));
      proxy.on('error', () => {
        record(req.method,path,502,started,device);
        if (!res.headersSent) json(res, 502, {status:502,message:'Không kết nối được CareFlow Gateway. Mở Kết nối hệ thống ở góc trên để kiểm tra Gateway.',data:null,timestamp:new Date().toISOString()});
        else res.destroy();
      });
      req.on('aborted', () => proxy.destroy());
      res.on('close', () => { if (!res.writableEnded) proxy.destroy(); });
      req.pipe(proxy);
      return;
    }
    if (!['GET','HEAD'].includes(req.method)) return json(res, 405, {message:'Method không hỗ trợ.'});
    // Only the web shell and built Flutter files are served, never source/env.
    const mobile = path === '/mobile' || path.startsWith('/mobile/');
    const base = resolve(staticRoot, mobile ? 'app/build/web' : 'web');
    let relative;
    try { relative = decodeURIComponent(mobile ? path.slice('/mobile'.length) : path); }
    catch { return json(res, 400, {message:'Đường dẫn không hợp lệ.'}); }
    if (relative.split('/').some(part => part === '..' || part.startsWith('.'))) return json(res, 404, {message:'Không tìm thấy.'});
    let file = resolve(base, '.' + (relative === '' || relative === '/' ? '/index.html' : relative));
    if (!file.startsWith(base + sep)) return json(res, 404, {message:'Không tìm thấy.'});
    try {
      const data = await readFile(file);
      res.writeHead(200, {'content-type':types[extname(file)] || 'application/octet-stream','cache-control':'no-cache'});
      res.end(req.method === 'HEAD' ? undefined : data);
    } catch {
      if (mobile && !extname(relative)) {
        try { res.writeHead(200, {'content-type':types['.html']}); res.end(await readFile(resolve(base,'index.html'))); }
        catch { json(res, 503, {message:'Chưa build Flutter. Chạy npm run build.'}); }
      } else json(res, 404, {message:'Không tìm thấy tệp. Nếu là mobile, chạy npm run build trước.'});
    }
  });
  server.on('upgrade', (req, socket, head) => {
    if (req.url !== '/ws/notifications' || !trustedBrowser(req)) return socket.destroy();
    const target = new URL(upstream);
    const proxy = (target.protocol === 'https:' ? https : http).request({hostname:target.hostname,port:target.port,path:req.url,headers:headers(req,target),timeout:10000});
    proxy.on('upgrade', (response, remote, upstreamHead) => {
      tunnels.add(socket);
      socket.write(`HTTP/1.1 ${response.statusCode} ${response.statusMessage}\r\n`);
      for (let i=0;i<response.rawHeaders.length;i+=2) socket.write(`${response.rawHeaders[i]}: ${response.rawHeaders[i+1]}\r\n`);
      socket.write('\r\n');
      if (head.length) remote.write(head);
      if (upstreamHead.length) socket.write(upstreamHead);
      socket.pipe(remote).pipe(socket);
      const close = () => { tunnels.delete(socket); remote.destroy(); socket.destroy(); };
      socket.on('error', close); remote.on('error', close);
      socket.on('close', () => { tunnels.delete(socket); remote.destroy(); });
      remote.on('close', () => socket.destroy());
    });
    proxy.on('response', response => {
      socket.end(`HTTP/1.1 ${response.statusCode} ${response.statusMessage}\r\nConnection: close\r\n\r\n`); response.resume();
    });
    proxy.on('timeout', () => proxy.destroy());
    proxy.on('error', () => socket.destroy());
    socket.on('close', () => proxy.destroy());
    proxy.end();
  });
  server.on('close', () => { for (const socket of tunnels) socket.destroy(); });
  return server;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 3000);
  const server = createEmulator();
  server.listen(port,'127.0.0.1', () => console.log(`CareFlow Mobile: http://localhost:${port}\nGateway: ${process.env.CAREFLOW_GATEWAY || DEFAULT_GATEWAY}\nChỉ chạy cục bộ. Ctrl+C để dừng.`));
  server.on('error', error => { console.error(error.code === 'EADDRINUSE' ? `Cổng ${port} đang được dùng. Chạy PORT=5173 npm start và cấu hình allowed origins của Gateway/Notification cho cổng này.` : error.message); process.exitCode=1; });
}
