"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth";

// Links change with the role: students see their appointments and the assistant, tutors their
// dashboard. Like RequireRole, this only shapes the page; the API enforces the rules.
export function Navbar() {
  const { user, ready, logout } = useAuth();

  return (
    <header className="navbar">
      <Link href="/" className="brand">
        TutorSlot
      </Link>
      <nav>
        <Link href="/slots">Find a slot</Link>
        {user?.role === "CUSTOMER" && (
          <>
            <Link href="/appointments">My appointments</Link>
            <Link href="/assistant">Assistant</Link>
          </>
        )}
        {user?.role === "PROVIDER" && (
          <>
            <Link href="/tutor">Dashboard</Link>
            <Link href="/tutor/appointments">Booked sessions</Link>
          </>
        )}
      </nav>
      <div className="account">
        {ready &&
          (user ? (
            <>
              <span className="muted">
                {user.fullName} ({user.role === "PROVIDER" ? "Tutor" : "Student"})
              </span>
              <button className="link-button" onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <Link href="/login">Log in</Link>
          ))}
      </div>
    </header>
  );
}
