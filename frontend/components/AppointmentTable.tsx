import type { ReactNode } from "react";
import { formatSession } from "@/lib/format";
import type { Appointment } from "@/lib/types";

// One table for both sides: a student sees the tutor's name, a tutor sees the student's.
export function AppointmentTable({
  appointments,
  show,
  action,
}: {
  appointments: Appointment[];
  show: "tutor" | "student";
  action?: (appointment: Appointment) => ReactNode;
}) {
  if (appointments.length === 0) {
    return <p className="muted">Nothing here yet.</p>;
  }
  return (
    <table>
      <thead>
        <tr>
          <th>Subject</th>
          <th>{show === "tutor" ? "Tutor" : "Student"}</th>
          <th>When</th>
          <th>Status</th>
          <th>Notes</th>
          {action && <th></th>}
        </tr>
      </thead>
      <tbody>
        {appointments.map((appointment) => (
          <tr key={appointment.appointmentId}>
            <td>{appointment.subjectName}</td>
            <td>{show === "tutor" ? appointment.tutorName : appointment.customerName}</td>
            <td>{formatSession(appointment.startTime, appointment.endTime)}</td>
            <td>
              <span className={`status ${appointment.status.toLowerCase()}`}>{appointment.status.toLowerCase()}</span>
            </td>
            <td className="muted">{appointment.notes}</td>
            {action && <td className="right">{action(appointment)}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
