"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth";

export default function HomePage() {
  const { user } = useAuth();

  return (
    <>
      <section className="hero">
        <h1>Book a tutor in seconds</h1>
        <p>
          Browse open sessions and book with one click, or just tell the AI assistant what you need: &quot;find me a
          Calculus slot this week and book it&quot;.
        </p>
        <div className="actions">
          <Link href="/slots" className="button">
            Find a slot
          </Link>
          {user?.role === "CUSTOMER" && (
            <Link href="/assistant" className="button secondary">
              Ask the assistant
            </Link>
          )}
          {user?.role === "PROVIDER" && (
            <Link href="/tutor" className="button secondary">
              Go to your dashboard
            </Link>
          )}
          {!user && (
            <Link href="/login" className="button secondary">
              Log in
            </Link>
          )}
        </div>
      </section>

      <section className="features">
        <div className="card">
          <h3>Book by chatting</h3>
          <p>The AI assistant finds slots, books, and cancels, and always asks before it changes anything.</p>
        </div>
        <div className="card">
          <h3>Answers you can trust</h3>
          <p>Questions about hours or cancellations are answered from TutorSlot&apos;s own policy document.</p>
        </div>
        <div className="card">
          <h3>Never miss a session</h3>
          <p>A reminder agent emails each student a short personal reminder the day before.</p>
        </div>
      </section>
    </>
  );
}
