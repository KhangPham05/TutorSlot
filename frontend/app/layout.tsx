import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Navbar } from "@/components/Navbar";
import { AuthProvider } from "@/lib/auth";
import "./globals.css";

export const metadata: Metadata = {
  title: "TutorSlot",
  description: "Book a tutoring session, or just ask the AI assistant to book it for you.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <Navbar />
          <main>{children}</main>
        </AuthProvider>
      </body>
    </html>
  );
}
