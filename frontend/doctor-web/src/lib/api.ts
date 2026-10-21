import { getAccessToken, setAccessToken, getSessionEpoch } from "./session-memory";
import { createProof } from "./dpop";
import { getErrorMessage } from "./error-utils";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "https://api.careflow-demo.online";

export interface ApiResponse<T> {
  status: string;
  message?: string;
  data: T;
}

export async function request<T>(path: string, options?: RequestInit, retried = false): Promise<ApiResponse<T>> {
  const url = `${BASE_URL}${path}`;
  console.log(`[API DEBUG] Requesting: ${url}`, options?.method || "GET");
  
  // Retrieve token from localStorage if available
  const token = getAccessToken();
  
  const isFormData = options?.body instanceof FormData;
  const headers: Record<string, string> = {
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
    ...(options?.headers as Record<string, string> || {}),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  // Auto-generate Idempotency-Key for state-mutating requests (POST, PUT) if not provided
  const method = (options?.method || "GET").toUpperCase();
  if ((method === "POST" || method === "PUT") && !headers["Idempotency-Key"]) {
    headers["Idempotency-Key"] = typeof crypto !== "undefined" && crypto.randomUUID 
      ? crypto.randomUUID() 
      : `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }

  headers["DPoP"] = await createProof(method, url, token);

  try {
    const sessionAction = ({"/api/auth/login":"login","/api/auth/passkeys/login/finish":"passkey","/api/auth/logout":"logout"} as Record<string,string>)[path];
    const response = await fetch(sessionAction ? `/api/session/${sessionAction}` : url, {
      ...options,
      headers,
    });

    console.log(`[API DEBUG] Response ${response.status} for ${url}`);

    // Handle No Content (204)
    if (response.status === 204) {
      return { status: "success", data: null as unknown as T };
    }

    if (response.status===401 && !retried && !["/api/auth/login","/api/auth/register","/api/auth/refresh","/api/auth/logout"].includes(path) && !(method === "POST" && ["/api/auth/login/", "/api/auth/recovery/", "/api/auth/registration/", "/api/auth/staff-passkeys/", "/api/auth/passkeys/", "/api/auth/login-methods"].some(prefix => path.startsWith(prefix)))) {
      await refreshAccessToken();
      return request<T>(path,options,true);
    }
    const resJson = await response.json().catch(() => ({}));
    
    if (!response.ok) {
      throw new Error(resJson.message || `Lỗi kết nối máy chủ (${response.status})`);
    }

    return resJson;
  } catch (err: unknown) {
    console.warn(`[API DEBUG] Request Error for ${url}:`, getErrorMessage(err));
    throw err;
  }
}

export const api = {
  get: <T>(path: string, options?: RequestInit) => request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body: unknown, options?: RequestInit) =>
    request<T>(path, { ...options, method: "POST", body: body instanceof FormData ? body : JSON.stringify(body) }),
  upload: <T>(path: string, formData: FormData, options?: RequestInit) =>
    request<T>(path, { ...options, method: "POST", body: formData }),
  put: <T>(path: string, body: unknown, options?: RequestInit) =>
    request<T>(path, { ...options, method: "PUT", body: JSON.stringify(body) }),
  delete: <T>(path: string, options?: RequestInit) => request<T>(path, { ...options, method: "DELETE" }),
};

let refreshing: Promise<void> | undefined;
export async function refreshAccessToken(): Promise<void> {
  if (refreshing) return refreshing;
  const epoch = getSessionEpoch();
  const execute = async () => {
    const target = `${BASE_URL}/api/auth/refresh`;
    const response = await fetch("/api/session/refresh", {
      method: "POST", headers: { "Content-Type": "application/json", DPoP: await createProof("POST", target) },
      body: "{}", credentials: "same-origin", cache: "no-store",
    });
    const result = await response.json();
    if (!response.ok) {
      if ((response.status === 401 || response.status === 403) && getSessionEpoch() === epoch) {
        setAccessToken(null);
        window.dispatchEvent(new Event("careflow:session-expired"));
      }
      throw new Error(result.message || "Cần đăng nhập lại.");
    }
    if (getSessionEpoch() !== epoch) throw new Error("Phiên đã thay đổi. Vui lòng thử lại.");
    setAccessToken(result.data.accessToken);
  };
  refreshing = (async () => {
    if (navigator.locks) await navigator.locks.request("careflow-refresh", execute);
    else await execute();
  })().finally(() => { refreshing = undefined; });
  return refreshing;
}
