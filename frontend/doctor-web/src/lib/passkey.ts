export async function passkeyResponse(serialized: string, register = false): Promise<string> {
  if (!window.isSecureContext || !window.PublicKeyCredential) throw new Error("Trình duyệt chưa hỗ trợ passkey. Hãy dùng localhost/HTTPS và trình duyệt mới.");
  const options = JSON.parse(serialized);
  const decode = (value: string) => Uint8Array.from(atob(value.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(value.length / 4) * 4, "=")), c => c.charCodeAt(0));
  const encode = (value: ArrayBuffer) => btoa(String.fromCharCode(...new Uint8Array(value))).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
  options.publicKey.challenge = decode(options.publicKey.challenge);
  if (options.publicKey.user) options.publicKey.user.id = decode(options.publicKey.user.id);
  for (const item of [...(options.publicKey.allowCredentials || []), ...(options.publicKey.excludeCredentials || [])]) item.id = decode(item.id);
  let credential: PublicKeyCredential | null;
  try { credential = await (register ? navigator.credentials.create(options) : navigator.credentials.get(options)) as PublicKeyCredential | null; }
  catch (error) {
    if (error instanceof DOMException && error.name === "NotAllowedError") throw new Error("Chưa xác nhận passkey, đã hủy hoặc thiết bị không có khóa phù hợp.");
    throw error;
  }
  if (!credential) throw new Error("Không nhận được passkey.");
  const response = credential.response;
  const common = {id: credential.id, rawId: encode(credential.rawId), type: credential.type, clientExtensionResults: credential.getClientExtensionResults()};
  if (register) {
    const r = response as AuthenticatorAttestationResponse;
    return JSON.stringify({...common, response: {clientDataJSON: encode(r.clientDataJSON), attestationObject: encode(r.attestationObject), transports: r.getTransports()}});
  }
  const r = response as AuthenticatorAssertionResponse;
  return JSON.stringify({...common, response: {clientDataJSON: encode(r.clientDataJSON), authenticatorData: encode(r.authenticatorData), signature: encode(r.signature), userHandle: r.userHandle ? encode(r.userHandle) : null}});
}
