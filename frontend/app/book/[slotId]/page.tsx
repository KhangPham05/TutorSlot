"use client";

import Link from "next/link";
import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { RequireRole } from "@/components/RequireRole";
import { api } from "@/lib/api";
import { formatSession } from "@/lib/format";
import type { Confirmation, Slot } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function BookPage() {
  return (
    <RequireRole role="CUSTOMER">
      <BookSlot />
    </RequireRole>
  );
}

function BookSlot() {
  const { slotId } = useParams<{ slotId: string }>();
  const router = useRouter();
  const { data: slot, error: loadError } = useApi<Slot>(`/slots/${slotId}`);
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // The server makes the real decision: if someone else booked this slot a moment ago, the
  // answer is a 409 with "This slot is already booked.", shown below the button.
  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      const booked = await api<Confirmation>("/customer/bookings", {
        method: "POST",
        body: { slotId: Number(slotId), notes },
      });
      router.push(`/appointments/${booked.appointmentId}`);
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  if (loadError) {
    return <p className="error">{loadError}</p>;
  }
  if (!slot) {
    return <p className="muted">Loading...</p>;
  }

  return (
    <section className="narrow">
      <h1>Book this session</h1>
      <div className="card">
        <h3>{slot.subjectName}</h3>
        <p>with {slot.tutorName}</p>
        <p>{formatSession(slot.startTime, slot.endTime)}</p>
      </div>
      <label className="form">
        Notes for your tutor (optional)
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={4} maxLength={1000} />
      </label>
      {error && (
        <p className="error">
          {error} <Link href="/slots">See other slots</Link>
        </p>
      )}
      <div className="actions">
        <button onClick={confirm} disabled={busy}>
          {busy ? "Booking..." : "Confirm booking"}
        </button>
        <Link href="/slots" className="button secondary">
          Back
        </Link>
      </div>
    </section>
  );
}
