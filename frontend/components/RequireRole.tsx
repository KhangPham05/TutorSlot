"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { homeFor, useAuth } from "@/lib/auth";
import type { Role } from "@/lib/types";

// Shows a page only to the right role. A guest goes to the login page and comes back here
// afterwards; someone logged in with the other role goes to their own start page. This is for a
// smooth experience only: the real check is the API's, which refuses any request without the
// right token.
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, ready } = useAuth();
  const router = useRouter();
  const allowed = user?.role === role;

  useEffect(() => {
    if (!ready || allowed) {
      return;
    }
    router.replace(user ? homeFor(user.role) : `/login?next=${encodeURIComponent(window.location.pathname)}`);
  }, [ready, allowed, user, router]);

  if (!ready || !allowed) {
    return <p className="muted">Loading...</p>;
  }
  return <>{children}</>;
}
