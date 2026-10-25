let pending: Promise<CryptoKeyPair> | undefined;
const bytes = (text: string) => new TextEncoder().encode(text);
const base64 = (value: ArrayBuffer | Uint8Array) => {
  let text = ""; for (const byte of new Uint8Array(value)) text += String.fromCharCode(byte);
  return btoa(text).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
};
const encode = (value: unknown) => base64(bytes(JSON.stringify(value)));
async function loadKeys(origin: string): Promise<CryptoKeyPair> {
  if (!window.isSecureContext || !crypto.subtle) throw new Error("Cần HTTPS hoặc localhost để bảo vệ đăng nhập.");
  const db = await new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(`careflow.hospital.dpop.${origin}`, 1);
    request.onupgradeneeded = () => request.result.createObjectStore("keys");
    request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
  });
  const existing = await new Promise<CryptoKeyPair | undefined>((resolve, reject) => {
    const request = db.transaction("keys").objectStore("keys").get("signing");
    request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
  });
  if (existing) { db.close(); return existing; }
  const generated = await crypto.subtle.generateKey({name: "ECDSA", namedCurve: "P-256"}, false, ["sign", "verify"]);
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("keys", "readwrite"), store = transaction.objectStore("keys");
    const request = store.get("signing"); let selected: CryptoKeyPair;
    request.onsuccess = () => { selected = request.result || generated; if (!request.result) store.put(selected, "signing"); };
    transaction.oncomplete = () => { db.close(); resolve(selected); };
    transaction.onerror = () => { db.close(); reject(transaction.error); };
  });
}
export async function createProof(method: string, target: string, token?: string | null) {
  const url = new URL(target, window.location.origin); url.search = ""; url.hash = "";
  // This module targets the one configured API origin; its keys never enter browser storage as text.
  const pair = await (pending ||= loadKeys(url.origin).catch(error => { pending = undefined; throw error; }));
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  const claims: Record<string, unknown> = {jti: crypto.randomUUID(), htm: method.toUpperCase(), htu: url.href, iat: Math.floor(Date.now() / 1000)};
  if (token) claims.ath = base64(await crypto.subtle.digest("SHA-256", bytes(token)));
  const content = encode({typ: "dpop+jwt", alg: "ES256", jwk}) + "." + encode(claims);
  return content + "." + base64(await crypto.subtle.sign({name: "ECDSA", hash: "SHA-256"}, pair.privateKey, bytes(content)));
}
