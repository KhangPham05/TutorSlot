"use client";

import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/lib/auth";
import { formatSession } from "@/lib/format";
import type { SlotsPage } from "@/lib/types";
import { useApi } from "@/lib/useApi";

// Open to everyone, like the API behind it. Changing a filter reloads the list from page 1.
export default function SlotsListPage() {
  const { user } = useAuth();
  const [providerId, setProviderId] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [date, setDate] = useState("");
  const [page, setPage] = useState(1);

  const query = new URLSearchParams({ page: String(page) });
  if (providerId) query.set("providerId", providerId);
  if (serviceId) query.set("serviceId", serviceId);
  if (date) query.set("date", date);
  const { data, error } = useApi<SlotsPage>(`/slots?${query}`);

  function filter(set: (value: string) => void) {
    return (value: string) => {
      set(value);
      setPage(1);
    };
  }

  return (
    <>
      <h1>Find a slot</h1>
      <div className="filters">
        <select value={providerId} onChange={(e) => filter(setProviderId)(e.target.value)}>
          <option value="">Any tutor</option>
          {data?.tutors.map((tutor) => (
            <option key={tutor.providerId} value={tutor.providerId}>
              {tutor.name}
            </option>
          ))}
        </select>
        <select value={serviceId} onChange={(e) => filter(setServiceId)(e.target.value)}>
          <option value="">Any subject</option>
          {data?.subjects.map((subject) => (
            <option key={subject.serviceId} value={subject.serviceId}>
              {subject.label}
            </option>
          ))}
        </select>
        <input type="date" value={date} onChange={(e) => filter(setDate)(e.target.value)} />
      </div>

      {error && <p className="error">{error}</p>}
      {data && data.slots.length === 0 && <p className="muted">No open slots match these filters.</p>}
      {data && data.slots.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Subject</th>
              <th>Tutor</th>
              <th>When</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.slots.map((slot) => (
              <tr key={slot.slotId}>
                <td>{slot.subjectName}</td>
                <td>{slot.tutorName}</td>
                <td>{formatSession(slot.startTime, slot.endTime)}</td>
                <td className="right">
                  {user?.role !== "PROVIDER" && (
                    <Link href={`/book/${slot.slotId}`} className="button small">
                      {user ? "Book" : "Log in to book"}
                    </Link>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {data && data.totalPages > 1 && (
        <div className="pager">
          <button disabled={page <= 1} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span className="muted">
            Page {data.currentPage} of {data.totalPages}
          </span>
          <button disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}>
            Next
          </button>
        </div>
      )}
    </>
  );
}
