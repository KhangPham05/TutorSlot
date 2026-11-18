"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api, clearSession, loadUser, saveSession } from "./api";
import type { LoginResponse, Role, User } from "./types";

// Who is logged in, available to every page through useAuth(). "ready" turns true once the saved
// session has been read, so pages don't flash the logged-out view on a refresh.
type Auth = {
  user: User | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => void;
};

const AuthContext = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  // sessionStorage only exists in the browser, so it's read after the first render.
  useEffect(() => {
    setUser(loadUser());
    setReady(true);
  }, []);

  async function login(email: string, password: string) {
    const response = await api<LoginResponse>("/auth/login", { method: "POST", body: { email, password } });
    saveSession(response.token, response.user);
    setUser(response.user);
    return response.user;
  }

  // A full reload to the home page, so no page that needs a login (and would send you to the
  // login page) gets to react to the logout first.
  function logout() {
    clearSession();
    window.location.assign("/");
  }

  return <AuthContext.Provider value={{ user, ready, login, logout }}>{children}</AuthContext.Provider>;
}

// Where each role starts: tutors on their dashboard, students on the slot list.
export function homeFor(role: Role) {
  return role === "PROVIDER" ? "/tutor" : "/slots";
}

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return auth;
}
