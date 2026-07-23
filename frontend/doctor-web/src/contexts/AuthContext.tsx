"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";

interface User {
  id: string;
  fullName: string;
  email: string;
  role: string;
  department: string;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Mock doctor accounts for development
const MOCK_DOCTORS: Record<string, User> = {
  "an@careflow.vn": {
    id: "d0000001-0000-0000-0000-000000000001",
    fullName: "BS. Nguyễn Văn An",
    email: "an@careflow.vn",
    role: "DOCTOR",
    department: "Nội tổng quát",
  },
  "binh@careflow.vn": {
    id: "d0000001-0000-0000-0000-000000000002",
    fullName: "BS. Trần Thị Bình",
    email: "binh@careflow.vn",
    role: "DOCTOR",
    department: "Nhi khoa",
  },
  "cuong@careflow.vn": {
    id: "d0000001-0000-0000-0000-000000000003",
    fullName: "BS. Lê Hoàng Cường",
    email: "cuong@careflow.vn",
    role: "DOCTOR",
    department: "Ngoại tổng quát",
  },
};

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    // Check localStorage for existing session
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
    // Simulate network delay
    await new Promise((resolve) => setTimeout(resolve, 800));

    // Mock authentication — accept any password for listed doctors
    const doctor = MOCK_DOCTORS[email];
    if (!doctor) {
      throw new Error("Email không tồn tại trong hệ thống");
    }

    if (!password || password.length < 3) {
      throw new Error("Mật khẩu không hợp lệ");
    }

    // Save mock token and user
    localStorage.setItem("careflow_token", `mock_jwt_${Date.now()}`);
    localStorage.setItem("careflow_user", JSON.stringify(doctor));
    setUser(doctor);
    router.push("/dashboard");
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
