"use client";

import { useEffect, useState, type FormEvent, type KeyboardEvent } from "react";
import { RequireRole } from "@/components/RequireRole";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { User } from "@/lib/types";
import { useApi } from "@/lib/useApi";

type ChatLine = { speaker: "You" | "Assistant"; text: string };

const EXAMPLES = [
  "What's your cancellation policy?",
  "Find the earliest Calculus slot this week and book it",
  "What are my upcoming sessions?",
  "Cancel my Calculus session",
];

export default function AssistantPage() {
  const { user } = useAuth();
  return <RequireRole role="CUSTOMER">{user && <Assistant user={user} />}</RequireRole>;
}

function Assistant({ user }: { user: User }) {
  const { data: status } = useApi<{ enabled: boolean }>("/customer/assistant");
  // The transcript lives in this tab only; the server keeps its own chat memory for the AI.
  const transcriptKey = `tutorslot.chat.${user.email}`;
  const [lines, setLines] = useState<ChatLine[]>([]);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const saved = sessionStorage.getItem(transcriptKey);
    setLines(saved ? (JSON.parse(saved) as ChatLine[]) : []);
  }, [transcriptKey]);

  function saveLines(next: ChatLine[]) {
    setLines(next);
    sessionStorage.setItem(transcriptKey, JSON.stringify(next));
  }

  async function send(event?: FormEvent) {
    event?.preventDefault();
    const text = message.trim();
    if (!text || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    const withQuestion: ChatLine[] = [...lines, { speaker: "You", text }];
    saveLines(withQuestion);
    setMessage("");
    try {
      // The student's id is never sent: the server reads it from the token. Booking still needs
      // a "yes" in a later message, enforced on the server (see AssistantTools).
      const { reply } = await api<{ reply: string }>("/customer/assistant", { method: "POST", body: { message: text } });
      saveLines([...withQuestion, { speaker: "Assistant", text: reply }]);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  // Enter sends, Shift+Enter starts a new line.
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void send();
    }
  }

  async function newConversation() {
    await api("/customer/assistant/reset", { method: "POST" });
    saveLines([]);
    setError(null);
  }

  if (!status) {
    return <p className="muted">Loading...</p>;
  }
  if (!status.enabled) {
    return (
      <>
        <h1>Assistant</h1>
        <p className="notice">The assistant is not configured.</p>
      </>
    );
  }

  return (
    <section className="chat">
      <div className="chat-header">
        <h1>Assistant</h1>
        <button className="secondary small" onClick={newConversation} disabled={busy}>
          New conversation
        </button>
      </div>
      <p className="muted">
        Ask about open slots, book or cancel a session, or ask about our policies. The assistant always shows the
        details and asks before it books or cancels anything.
      </p>

      <div className="transcript">
        {lines.length === 0 && (
          <div className="examples">
            {EXAMPLES.map((example) => (
              <button key={example} className="chip" onClick={() => setMessage(example)}>
                {example}
              </button>
            ))}
          </div>
        )}
        {lines.map((line, index) => (
          <div key={index} className={`bubble ${line.speaker === "You" ? "mine" : "theirs"}`}>
            {line.text}
          </div>
        ))}
        {busy && <div className="bubble theirs muted">Thinking...</div>}
      </div>

      {error && <p className="error">{error}</p>}
      <form onSubmit={send} className="chat-input">
        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message"
          rows={2}
          maxLength={1000}
        />
        <button type="submit" disabled={busy || !message.trim()}>
          Send
        </button>
      </form>
    </section>
  );
}
