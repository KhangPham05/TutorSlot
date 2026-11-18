"use client";

import { useState, type FormEvent } from "react";
import { RequireRole } from "@/components/RequireRole";
import { api } from "@/lib/api";
import { formatSession } from "@/lib/format";
import type { TutorDashboard, TutorSlot } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function TutorDashboardPage() {
  return (
    <RequireRole role="PROVIDER">
      <Dashboard />
    </RequireRole>
  );
}

function Dashboard() {
  const { data, error, reload } = useApi<TutorDashboard>("/provider/dashboard");
  const [serviceId, setServiceId] = useState("");
  const [startDateTime, setStartDateTime] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Runs one dashboard action, then shows its message (or the server's error) and reloads.
  async function run(action: () => Promise<string>) {
    setBusy(true);
    setMessage(null);
    setActionError(null);
    try {
      setMessage(await action());
      reload();
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function addSlot(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      // The datetime-local value ("2026-11-19T15:00") is exactly the format the API reads.
      await api("/provider/slots", { method: "POST", body: { serviceId: Number(serviceId), startDateTime } });
      setStartDateTime("");
      return "Slot added.";
    });
  }

  function removeSlot(slot: TutorSlot) {
    if (window.confirm(`Remove your ${slot.subjectName} slot on ${formatSession(slot.startTime, slot.endTime)}?`)) {
      void run(async () => {
        await api(`/provider/slots/${slot.slotId}/delete`, { method: "POST" });
        return "Slot removed.";
      });
    }
  }

  function sendReminders() {
    void run(async () => {
      const { sent } = await api<{ sent: number }>("/provider/reminders/run", { method: "POST" });
      return sent === 1 ? "1 reminder sent." : `${sent} reminders sent.`;
    });
  }

  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return <p className="muted">Loading...</p>;
  }

  return (
    <>
      <h1>Dashboard</h1>
      {message && <p className="notice">{message}</p>}
      {actionError && <p className="error">{actionError}</p>}

      <h2>Add a slot</h2>
      <form onSubmit={addSlot} className="filters">
        <select value={serviceId} onChange={(e) => setServiceId(e.target.value)} required>
          <option value="">Pick a subject</option>
          {data.subjects.map((subject) => (
            <option key={subject.serviceId} value={subject.serviceId}>
              {subject.name}
            </option>
          ))}
        </select>
        <input
          type="datetime-local"
          value={startDateTime}
          onChange={(e) => setStartDateTime(e.target.value)}
          required
        />
        <button type="submit" disabled={busy}>
          Add slot
        </button>
      </form>
      <p className="muted small">Sessions are 60 minutes.</p>

      <h2>Your slots</h2>
      {data.slots.length === 0 ? (
        <p className="muted">No slots yet.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Subject</th>
              <th>When</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.slots.map((slot) => (
              <tr key={slot.slotId}>
                <td>{slot.subjectName}</td>
                <td>{formatSession(slot.startTime, slot.endTime)}</td>
                <td>
                  <span className={`status ${slot.booked ? "booked" : "open"}`}>{slot.booked ? "booked" : "open"}</span>
                </td>
                <td className="right">
                  {!slot.booked && (
                    <button className="small danger" onClick={() => removeSlot(slot)} disabled={busy}>
                      Remove
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>Reminders</h2>
      <p className="muted">
        Emails a reminder for each session in the next 24 hours. Also runs by itself every 15 minutes. The AI writes
        each one, so this can take a few seconds per session.
      </p>
      <button onClick={sendReminders} disabled={busy}>
        {busy ? "Working..." : "Send reminders now"}
      </button>
    </>
  );
}
