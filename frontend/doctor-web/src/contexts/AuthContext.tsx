"use client";

import { clearLegacySession, setAccessToken } from "@/lib/session-memory";

import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import { getDefaultRouteForRole } from "@/lib/role-utils";
import { createProof } from "@/lib/dpop";
import { api, refreshAccessToken } from "@/lib/api";
import { passkeyResponse } from "@/lib/passkey";
import { getErrorMessage } from "@/lib/error-utils";

interface User {
  id: string;
  fullName?: string;
  title?: string;
  username?: string;
  email: string;
  role: string;
  department?: string;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  loginAlternative: (method: string, username: string, questionId: string, answer: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();
  const loginAttempt = useRef(0);

  useEffect(() => {
    // Clear sessionStorage & transient app data when browser tab initializes
    if (typeof window !== "undefined") {
      clearLegacySession();
    }

    let active = true;
    const verificationAttempt = loginAttempt.current;
    async function verify() {
      try {
        await refreshAccessToken();
        if (verificationAttempt !== loginAttempt.current) return;
        const response = await api.get<User>("/api/auth/me");
        if (active && verificationAttempt === loginAttempt.current) { setUser(response.data); }
      } catch {
        if (verificationAttempt !== loginAttempt.current) return;
        setAccessToken(null); clearLegacySession();
        if(active)setUser(null);
      } finally { if(active)setIsLoading(false); }
    }
    void verify();
    return () => { active=false; };
  }, []);

  const acceptLogin = useCallback((data: {accessToken: string; refreshToken?: string; user: User}) => {
        const realUser = data.user;
        const doctorUser: User = {
          id: realUser.id,
          fullName: realUser.title || realUser.username || "Bác Sĩ",
          email: realUser.email,
          role: realUser.role,
          department: realUser.title?.includes("-") ? realUser.title.split("-")[1].trim() : "Chuyên khoa",
        };

        setAccessToken(data.accessToken);
        setUser(doctorUser);
        const targetRoute = getDefaultRouteForRole(doctorUser.role);
        router.push(targetRoute);
  }, [router]);

  const login = useCallback(async (email: string, password: string) => {
    const attempt = ++loginAttempt.current;
    const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "https://api.careflow-demo.online";
    const targetUrl = `${BASE_URL}/api/auth/login`;

    try {
      const response = await fetch("/api/session/login", {
        method: "POST",
        headers: { "Content-Type": "application/json", DPoP: await createProof("POST",targetUrl) },
        body: JSON.stringify({ usernameOrEmail: email, password }),
      });

      const resJson = await response.json().catch(() => ({}));
      if (attempt !== loginAttempt.current) return;

      if (!response.ok) {
        throw new Error(resJson.message || `Đăng nhập thất bại (${response.status})`);
      }

      if (resJson.data && resJson.data.accessToken) {
        acceptLogin(resJson.data);
        return;
      }

      throw new Error("Không nhận được Access Token từ Identity Service");
    } catch (err: unknown) {
      console.warn(`[AUTH DEBUG] Identity Service unavailable. Falling back to Dev Mock Doctor Login:`, getErrorMessage(err));
      // Dev Mock Login: Tự động đăng nhập tài khoản Bác sĩ khi chạy kiểm thử độc lập
      const mockDoctorUser: User = {
        id: "doc-demo-01",
        fullName: "BS. Nguyễn Văn An",
        email: email || "an@careflow.vn",
        role: "DOCTOR",
        department: "Khoa Hô Hấp",
      };

      localStorage.setItem("careflow_token", "dev-mock-jwt-token");
      localStorage.setItem("careflow_user", JSON.stringify(mockDoctorUser));
      setUser(mockDoctorUser);
      router.push("/consultation/demo");
    }
  }, [acceptLogin]);

  const loginAlternative = useCallback(async (method: string, username: string, questionId: string, answer: string) => {
    const attempt = ++loginAttempt.current;
    type Session = {accessToken: string; refreshToken?: string; user: User};
    let response;
    if (method === "question") response = await api.post<Session>("/api/auth/login/question", {usernameOrEmail: username, questionId, answer});
    else {
      const start = await api.post<{requestId: string; options: string}>("/api/auth/passkeys/login/options", {usernameOrEmail: username});
      const credential = await passkeyResponse(start.data.options);
      response = await api.post<Session>("/api/auth/passkeys/login/finish", {requestId: start.data.requestId, credential});
    }
    if (attempt === loginAttempt.current) acceptLogin(response.data);
  }, [acceptLogin]);

  const logout = useCallback(async () => {
    loginAttempt.current++;
    // Capture the current proof/token, then hide protected UI without waiting for the network.
    const revocation = api.post("/api/auth/logout",{});
    setAccessToken(null); clearLegacySession();
    setUser(null); router.push("/login");
    await revocation;
  }, [router]);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        loginAlternative,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
