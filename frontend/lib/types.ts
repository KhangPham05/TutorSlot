// The JSON the Spring Boot API returns, one type per DTO record. Times are local date-times
// without a time zone ("2026-11-19T13:00:00"), exactly as the tutors entered them.

export type Role = "CUSTOMER" | "PROVIDER";

export type User = { email: string; fullName: string; role: Role };

export type LoginResponse = { token: string; expiresAt: string; user: User };

export type Slot = {
  slotId: number;
  tutorName: string;
  subjectName: string;
  startTime: string;
  endTime: string;
};

export type SlotsPage = {
  slots: Slot[];
  tutors: { providerId: number; name: string }[];
  subjects: { serviceId: number; label: string }[];
  currentPage: number;
  totalPages: number;
};

export type Appointment = {
  appointmentId: number;
  tutorName: string;
  customerName: string;
  subjectName: string;
  startTime: string;
  endTime: string;
  status: "BOOKED" | "CANCELLED" | "COMPLETED";
  notes: string | null;
};

export type MyAppointments = { upcoming: Appointment[]; history: Appointment[] };

export type Confirmation = {
  appointmentId: number;
  tutorName: string;
  subjectName: string;
  startTime: string;
  endTime: string;
  notes: string | null;
};

export type TutorSlot = {
  slotId: number;
  subjectName: string;
  startTime: string;
  endTime: string;
  booked: boolean;
};

export type TutorDashboard = {
  slots: TutorSlot[];
  subjects: { serviceId: number; name: string }[];
};
