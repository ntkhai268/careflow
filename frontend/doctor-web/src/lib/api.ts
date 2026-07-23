const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface ApiResponse<T> {
  status: string;
  message?: string;
  data: T;
}

export async function request<T>(path: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const url = `${BASE_URL}${path}`;
  
  // Retrieve token from localStorage if available
  const token = typeof window !== "undefined" ? localStorage.getItem("careflow_token") : null;
  
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options?.headers as Record<string, string> || {}),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // Handle No Content (204)
  if (response.status === 204) {
    return { status: "success", data: {} as T };
  }

  const resJson = await response.json().catch(() => ({}));
  
  if (!response.ok) {
    throw new Error(resJson.message || `Lỗi kết nối máy chủ (${response.status})`);
  }

  return resJson;
}

export const api = {
  get: <T>(path: string, options?: RequestInit) => request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body: any, options?: RequestInit) => 
    request<T>(path, { ...options, method: "POST", body: JSON.stringify(body) }),
  put: <T>(path: string, body: any, options?: RequestInit) => 
    request<T>(path, { ...options, method: "PUT", body: JSON.stringify(body) }),
  delete: <T>(path: string, options?: RequestInit) => request<T>(path, { ...options, method: "DELETE" }),
};
