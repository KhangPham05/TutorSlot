"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { RequireRole } from "@/components/RequireRole";
import { formatSession } from "@/lib/format";
import type { Confirmation } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function ConfirmationPage() {
  return (
    <RequireRole role="CUSTOMER">
      <BookingConfirmation />
    </RequireRole>
  );
}

function BookingConfirmation() {
  const { id } = useParams<{ id: string }>();
  const { data, error } = useApi<Confirmation>(`/customer/appointments/${id}`);

  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return <p className="muted">Loading...</p>;
  }
  return (
    <section className="narrow">
      <h1>You&apos;re booked!</h1>
      <div className="card">
        <h3>{data.subjectName}</h3>
        <p>with {data.tutorName}</p>
        <p>{formatSession(data.startTime, data.endTime)}</p>
        {data.notes && <p className="muted">Notes: {data.notes}</p>}
      </div>
      <p className="muted">A confirmation email is on its way. You&apos;ll also get a reminder the day before.</p>
      <div className="actions">
        <Link href="/appointments" className="button">
          My appointments
        </Link>
        <Link href="/slots" className="button secondary">
          Book another
        </Link>
      </div>
    </section>
  );
}
