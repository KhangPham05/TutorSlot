import type { User } from "./types";

// Every call to the Spring Boot API goes through api(): it adds the JWT from login, turns an
// error response into an ApiError carrying the server's short message, and sends the user back
// to the login page when the token is missing or has expired.

// sessionStorage, not localStorage: a refresh keeps you logged in, closing the tab logs you out.
const TOKEN_KEY = "tutorslot.token";
const USER_KEY = "tutorslot.user";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export function saveSession(token: string, user: User) {
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function loadUser(): User | null {
  const saved = sessionStorage.getItem(USER_KEY);
  return saved ? (JSON.parse(saved) as User) : null;
}

// Logging out forgets everything this app saved in the tab: the token, the user, and any chat.
export function clearSession() {
  Object.keys(sessionStorage)
    .filter((key) => key.startsWith("tutorslot."))
    .forEach((key) => sessionStorage.removeItem(key));
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(`/api${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  // An expired or missing token: start over at the login page. (A wrong password on the login
  // page itself is also a 401, and that one is shown on the form instead.)
  if (response.status === 401 && path !== "/auth/login") {
    clearSession();
    window.location.assign(`/login?expired=1&next=${encodeURIComponent(window.location.pathname)}`);
    throw new ApiError(401, "Please log in again.");
  }
  if (!response.ok) {
    const data = await response.json().catch(() => null);
    const fallback = response.status === 403 ? "You can't do that." : "Something went wrong. Please try again.";
    throw new ApiError(response.status, data?.error ?? fallback);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}
