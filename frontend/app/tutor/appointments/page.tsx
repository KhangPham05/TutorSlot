"use client";

import { AppointmentTable } from "@/components/AppointmentTable";
import { RequireRole } from "@/components/RequireRole";
import type { MyAppointments } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function TutorAppointmentsPage() {
  return (
    <RequireRole role="PROVIDER">
      <BookedSessions />
    </RequireRole>
  );
}

function BookedSessions() {
  const { data, error } = useApi<MyAppointments>("/provider/appointments");

  return (
    <>
      <h1>Booked sessions</h1>
      {error && <p className="error">{error}</p>}
      {data && (
        <>
          <h2>Upcoming</h2>
          <AppointmentTable appointments={data.upcoming} show="student" />
          <h2>History</h2>
          <AppointmentTable appointments={data.history} show="student" />
        </>
      )}
    </>
  );
}
