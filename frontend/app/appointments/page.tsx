"use client";

import { useState } from "react";
import { AppointmentTable } from "@/components/AppointmentTable";
import { RequireRole } from "@/components/RequireRole";
import { api } from "@/lib/api";
import type { Appointment, MyAppointments } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function AppointmentsPage() {
  return (
    <RequireRole role="CUSTOMER">
      <MyAppointmentsList />
    </RequireRole>
  );
}

function MyAppointmentsList() {
  const { data, error, reload } = useApi<MyAppointments>("/customer/appointments");
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function cancel(appointment: Appointment) {
    if (!window.confirm(`Cancel your ${appointment.subjectName} session with ${appointment.tutorName}?`)) {
      return;
    }
    setMessage(null);
    setActionError(null);
    try {
      await api(`/customer/appointments/${appointment.appointmentId}/cancel`, { method: "POST" });
      setMessage("Appointment cancelled.");
      reload();
    } catch (e) {
      setActionError((e as Error).message);
    }
  }

  return (
    <>
      <h1>My appointments</h1>
      {message && <p className="notice">{message}</p>}
      {(error || actionError) && <p className="error">{error ?? actionError}</p>}
      {data && (
        <>
          <h2>Upcoming</h2>
          <AppointmentTable
            appointments={data.upcoming}
            show="tutor"
            action={(appointment) => (
              <button className="small danger" onClick={() => cancel(appointment)}>
                Cancel
              </button>
            )}
          />
          <h2>History</h2>
          <AppointmentTable appointments={data.history} show="tutor" />
        </>
      )}
    </>
  );
}
