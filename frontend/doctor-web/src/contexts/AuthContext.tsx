"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";

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
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    // Purge legacy mock token if present
    const savedToken = localStorage.getItem("careflow_token");
    if (savedToken && savedToken.startsWith("mock_jwt_")) {
      localStorage.removeItem("careflow_user");
      localStorage.removeItem("careflow_token");
      setUser(null);
      setIsLoading(false);
      return;
    }

    const savedUser = localStorage.getItem("careflow_user");
    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch {
        localStorage.removeItem("careflow_user");
        localStorage.removeItem("careflow_token");
      }
    }
    setIsLoading(false);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "https://api.careflow-demo.online";
    const targetUrl = `${BASE_URL}/api/auth/login`;
    console.log(`[AUTH DEBUG] Attempting login to: ${targetUrl}`, { email });

    try {
      const response = await fetch(targetUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ usernameOrEmail: email, password: password }),
      });

      console.log(`[AUTH DEBUG] Response status: ${response.status}`, response);
      const resJson = await response.json().catch(() => ({}));
      console.log(`[AUTH DEBUG] Response JSON:`, resJson);

      if (!response.ok) {
        throw new Error(resJson.message || `Đăng nhập thất bại (${response.status})`);
      }

      if (resJson.data && resJson.data.accessToken) {
        const realUser = resJson.data.user;
        const doctorUser: User = {
          id: realUser.id,
          fullName: realUser.title || realUser.username || "Bác Sĩ",
          email: realUser.email,
          role: realUser.role,
          department: realUser.title?.includes("-") ? realUser.title.split("-")[1].trim() : "Chuyên khoa",
        };

        localStorage.setItem("careflow_token", resJson.data.accessToken);
        localStorage.setItem("careflow_user", JSON.stringify(doctorUser));
        setUser(doctorUser);
        router.push("/dashboard");
        return;
      }

      throw new Error("Không nhận được Access Token từ Identity Service");
    } catch (err: any) {
      console.error(`[AUTH DEBUG] Login Error:`, err);
      throw err;
    }
  }, [router]);

  const logout = useCallback(() => {
    localStorage.removeItem("careflow_token");
    localStorage.removeItem("careflow_user");
    setUser(null);
    router.push("/login");
  }, [router]);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
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
