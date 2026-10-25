const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const ts = require(path.join(root, 'frontend/doctor-web/node_modules/typescript'));
const load = (filename, dependencies, globals) => {
  const source = ts.transpileModule(fs.readFileSync(path.join(root, filename), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(source, { module, exports: module.exports, require: name => dependencies[name], process: { env: {} }, ...globals });
  return module.exports;
};
(async () => {
  const storage = new Map([['careflow_refresh_token', 'legacy-fixture']]);
  const memory = load('frontend/doctor-web/src/lib/session-memory.ts', {}, { localStorage: { removeItem: name => storage.delete(name) } });
  memory.clearLegacySession();assert.equal(storage.size, 0);
  let resolveResponse, calls = 0;
  const api = load('frontend/doctor-web/src/lib/api.ts', {
    './session-memory': memory, './dpop': { createProof: async () => 'proof' }, './error-utils': { getErrorMessage: error => error.message },
  }, {
    navigator: { locks: { request: async (_name, action) => action() } },
    fetch: async () => { calls++; return new Promise(resolve => { resolveResponse = resolve; }); }, console,
  });
  const waitForCall = async expected => {
    for (let i = 0; i < 20; i++) { if (calls >= expected) return; await new Promise(setImmediate); }
    throw new Error("Expected refresh fetch did not start");
  };
  const reply = token => resolveResponse({ ok: true, json: async () => ({ data: { accessToken: token } }) });
  const stale = api.refreshAccessToken();await waitForCall(1);
  memory.setAccessToken('new-account-session');reply('old-account-session');
  await assert.rejects(stale);assert.equal(memory.getAccessToken(), 'new-account-session');
  const current = api.refreshAccessToken();const same = api.refreshAccessToken();await waitForCall(2);
  assert.equal(calls, 2);reply('renewed-session');await Promise.all([current, same]);
  assert.equal(memory.getAccessToken(), 'renewed-session');
  const loggingOut = api.refreshAccessToken();await waitForCall(3);memory.setAccessToken(null);reply('late-session');
  await assert.rejects(loggingOut);assert.equal(memory.getAccessToken(), null);
  console.log('Web memory security: legacy cleanup, account race, single-flight and logout race passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
