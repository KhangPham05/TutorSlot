"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { homeFor, useAuth } from "@/lib/auth";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    setExpired(new URLSearchParams(window.location.search).has("expired"));
  }, []);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user = await login(email, password);
      // Back to the page that asked for a login. Only a path on this site, never another website.
      const next = new URLSearchParams(window.location.search).get("next");
      const safeNext = next && next.startsWith("/") && !next.startsWith("//") ? next : null;
      router.push(safeNext ?? homeFor(user.role));
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <section className="narrow">
      <h1>Log in</h1>
      {expired && <p className="notice">Your session ended. Please log in again.</p>}
      <form onSubmit={handleSubmit} className="form">
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>
          {busy ? "Logging in..." : "Log in"}
        </button>
      </form>
      <p className="muted small">
        Demo accounts: dan.student@sjsu.edu (student) or alice.tutor@sjsu.edu (tutor), password password123.
      </p>
    </section>
  );
}
