# TutorSlot

[![CI](https://github.com/KhangPham05/TutorSlot/actions/workflows/ci.yml/badge.svg)](https://github.com/KhangPham05/TutorSlot/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.8-6DB33F)

TutorSlot is a tutoring appointment booking app with a built-in **AI booking agent**. Students
can browse tutors' open time slots and book on the web, or just tell the assistant what they
need in plain language: *"find me a Calculus slot this week and book it"*, *"cancel my Physics
session"*, *"what's your cancellation policy?"*. Tutors publish the hours they teach, and a
second agent works in the background, sending each student a short personal reminder before
their session.

What it does:

- **Book by chatting with an AI agent.** The assistant finds open slots, books and cancels
  sessions, and lists your appointments. It always shows the details and asks before it books or
  cancels anything.
- **Policy answers you can trust.** Questions about hours, cancellations, or no-shows are
  answered from TutorSlot's own policy document (RAG), never made up.
- **Automatic reminders.** A scheduled agent writes a friendly reminder for every session in the
  next 24 hours and emails it to the student.
- **Booking on the web too.** Browse, filter, book, and cancel slots; tutors manage their slots
  from a dashboard.

Under the hood it's built to behave like a real service: **race-free booking** (two students can
never win the same slot, whether they use the web page or the agent), a **real network boundary**
to a notification service that can be killed and slowed down on command, and the **ops basics**:
logs, health, metrics, Docker, and CI. The AI runs on Spring AI and the free Gemini API, and it's
optional: without an API key the app still runs, the chat assistant is simply switched off, and
reminders go out as a plain template.

## Quick start

Needs Java 21 and a local PostgreSQL with a `tutorslot_db` database (`postgres`/`postgres` by
default — override with the `DB_USERNAME`/`DB_PASSWORD` env vars). No Postgres handy? Skip to
[Docker](#docker) instead, which brings its own.

```bash
./mvnw spring-boot:run
```
Recreates the schema and loads sample data on every startup — nothing to configure first.
Visit **http://localhost:8080** and log in with a seeded account:

| Role | Email | Password |
|---|---|---|
| Customer | `dan.student@sjsu.edu` | `password123` |
| Provider | `alice.tutor@sjsu.edu` | `password123` |

(8 seeded users total — full roster in `src/main/resources/seed.sql`. Every account shares the
same password.)

**Turn on the AI agents (optional).** Get a free Gemini API key (see
[Setup](#setup-a-free-gemini-key)), put it in a `.env` file in the project root, and start the app
with it loaded:
```bash
echo 'GEMINI_API_KEY=your-key-here' > .env
set -a; source .env; set +a
./mvnw spring-boot:run
```
Without a key everything else works the same, and the Assistant page says it isn't configured.

Prefer containers?
```bash
docker compose up --build
```
Two services, `db` + `app` — see [Docker](#docker) below for the stop/start failure demo.

## At a glance

| | |
|---|---|
| **Stack** | Java 21 · Spring Boot 3.5.3 · PostgreSQL · plain SQL via `JdbcTemplate` (no ORM) |
| **Auth** | Spring Security, session-based, two roles (`CUSTOMER` / `PROVIDER`), CSRF on |
| **Concurrency** | Pessimistic row locks + a partial unique index as the real backstop — see below |
| **Integration** | A real HTTP boundary to a mock notification service — timeouts, retry, idempotent fallback |
| **Ops** | `/health`, Micrometer metrics, structured logs, Docker, GitHub Actions CI |
| **AI** | Spring AI 1.1.8 · Gemini free tier · tool calling, chat memory, a scheduled agent, RAG over a knowledge file |
| **Tests** | 64 tests — unit (Mockito) + integration (`@SpringBootTest`, real Postgres) — 3 clean runs in a row, none calls Gemini |

## How the AI works

Three AI features live in `com.tutorslot.ai`. Each one is a thin layer over the same services
the web pages use, so the agents follow exactly the same rules as a person clicking buttons.

### The booking agent

Students click **Assistant** in the navbar (`/customer/assistant`) and chat in plain language:

- *What's your cancellation policy?*
- *Find the earliest Calculus slot this week and book it* (it shows the slot and asks "Shall I
  confirm?"; reply *yes*)
- *What sessions do I have coming up?*
- *Cancel my Calculus session*

**New conversation** clears the chat. Gemini answers by calling six tools in `AssistantTools`,
each a thin wrapper around an existing service:

| Tool | Calls |
|---|---|
| `findAvailableSlots` | `AvailabilitySlotService.getAvailableSlotsPage` (up to 3 pages, filtered in Java, no new SQL) |
| `listMyAppointments` | `AppointmentService.getMyAppointments` |
| `proposeBooking`, `proposeCancellation` | Only save a proposal. Nothing is written |
| `confirmPendingAction` | `BookingFacade.book` or `AppointmentService.cancel` |
| `searchPolicies` | The knowledge file's vector store (see below) |

The safety rules are enforced in code, not just asked for in the prompt:

- **The customer id never comes from the AI.** The controller takes it from the login session and
  passes it to the tools through Spring AI's *tool context*, which the model never sees. No tool
  has a user id parameter, so no message can make it book for someone else.
- **Confirm before writing.** Each proposal remembers the turn (message number) it was made in,
  and `confirmPendingAction` only carries out a proposal from an *earlier* turn. So nothing is
  booked or cancelled until the student has seen the details and sent a new message, even if the
  model tries to do it in one step.
- **Same rules as the web pages.** Writes go through `BookingFacade` and
  `AppointmentService.cancel`, so the row lock, the unique index, the owner check, and the
  confirmation email all still apply. If a slot was taken in the meantime, the tool returns
  "already booked" together with the next open slots, and the assistant offers those.
- If Gemini is slow or down, the page shows a short "try again" message, never a stack trace.

### The reminder agent

`ReminderAgent` runs every 15 minutes. For each booked session starting in the next 24 hours that
hasn't had a reminder (at most 5 per run, to protect the free quota), it asks Gemini for a short,
friendly reminder and sends it through the same `NotificationGateway` as booking emails:

- Idempotency key `REMINDER-{appointmentId}`, so a retry or a resend can never email twice.
- If Gemini fails, or there's no key, it sends a template instead: *"Reminder: your {subject}
  session with {tutor} is on {time}."*
- If the notification service is down, the reminder waits in `pending_notifications` and the
  resend job delivers it later.
- The appointment's `reminder_sent_at` is then set, so the next run doesn't pay for it again.

Tutors can run it right away with **Send reminders now** on the dashboard
(`POST /provider/reminders/run`). The seed data has three sessions tomorrow (10 AM, 11 AM, and
1 PM), so a click sends a reminder for each one that starts within the next 24 hours (all three
in the afternoon), and a second click sends none. To read what was sent:

```bash
curl http://localhost:8080/mock-notify/notifications -H "X-Api-Key: dev-key"
```

### Policy answers from a knowledge file (RAG)

`src/main/resources/knowledge/tutorslot-policies.md` covers tutoring hours, how booking works,
the cancellation and no-show policies, subjects and tutors, session length, reminders, and how to
get help. At startup (only with a key) it's split into one passage per `##` section and embedded
with `gemini-embedding-001` into an in-memory `SimpleVectorStore`. The `searchPolicies` tool
returns the 3 closest passages, and the assistant answers only from them; if none fits, it says
it doesn't know. Edit the file and restart to change the answers. A policy question costs one
embedding call, and booking messages cost none.

### Setup: a free Gemini key

1. Open [Google AI Studio](https://aistudio.google.com), sign in, and choose **Get API key**, then
   **Create API key**. Create it in a project with no billing account, so it can only ever use
   the free tier.
2. Save it in a `.env` file in the project root: `GEMINI_API_KEY=your-key-here`. `.env` is in
   `.gitignore` and `.dockerignore`, so it is never committed or copied into the Docker image.
3. Locally, load it with `set -a; source .env; set +a` before `./mvnw spring-boot:run`. Docker
   Compose reads `.env` by itself.

At startup the log says `Gemini AI features enabled`, or `disabled (no GEMINI_API_KEY set)`.
Only the API key is configured, never `project-id` or `location`: those switch Spring AI to
Vertex AI, which rejects a free key.

| Setting | Default | What it is |
|---|---|---|
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Chat model, free tier. Chosen after timing the free Flash models; others were slower or overloaded |
| embedding model | `gemini-embedding-001` | Turns the knowledge file into vectors |
| `gemini.timeout-ms`, `gemini.max-attempts` | `20000`, `2` | Upper limit for one Gemini call, so a slow free tier can't hang a page |

**With no key**, nothing calls Gemini: the Assistant page says "The assistant is not
configured.", reminders use a plain template, and the knowledge file isn't embedded.

### Privacy and logs

- **Sent to Gemini:** the student's chat messages and the tool results (open slots, their own
  appointments, policy passages). For reminders, only the student's first name, the subject, the
  tutor, and the time. Never emails, passwords, or appointment notes.
- **Logged:** one line per tool call (`AI tool call: tool=proposeBooking user=5 slot=7`) and per
  reminder (`Reminder sent: appointment=12 source=ai`). Never the student's messages, their
  notes, the reminder text, or the API key.

## Architecture

![Controller to Service to Repository to PostgreSQL](docs/architecture-diagram.svg)

One direction only. Controllers touch HTTP and Thymeleaf views, never SQL or business rules.
Services hold the business rules — including the booking transaction — and shape repository
models into view-facing DTOs. Repositories are one class per table, plain `JdbcTemplate` /
`NamedParameterJdbcTemplate` + `RowMapper`, nothing generated. A small, centralized
`GlobalExceptionHandler` turns three custom exceptions (`NotFoundException`,
`ForbiddenException`, `SlotConflictException`) into the right HTTP status and error page — a
stack trace never reaches the browser.

Notifications live behind their own boundary:

![Booking commits, then notifies async; failed attempts queue and get resent](docs/notification-diagram.svg)

`com.tutorslot.notification` (this app) and `com.tutorslot.mocknotification` (standing in for a
real third party) share **zero Java classes** — only an agreed JSON shape, like any real
external API integration. Booking and cancelling publish an event *inside* their own
transaction; the listener only fires `AFTER_COMMIT`, off the request thread (`@Async`), so a
slow notification service can never hold a booking's row lock or make a student wait. If every
retry fails, the notification lands in `pending_notifications` instead of being lost, and a
scheduled job keeps retrying it with the same idempotency key until it succeeds.

<details>
<summary><strong>Booking concurrency, in depth</strong> — how two students racing for the same slot can never both win</summary>

<br>

**`BookingService.book(slotId, customerId, notes)`** is `@Transactional` at `READ COMMITTED`
(Postgres's default, set explicitly so it's visible in the code) and does, in order:
1. `SELECT ... FROM availability_slots WHERE slot_id = ? FOR UPDATE` — locks the row. A second
   transaction trying to book the same slot blocks here until the first commits or rolls back.
2. Checks the slot's `start_time` is still in the future.
3. Checks whether the slot already has an active `BOOKED` appointment.
4. Inserts the new appointment.

Why `READ COMMITTED` specifically matters: because it re-reads on every new statement, the second
(now-unblocked) transaction's step 3 check sees the first transaction's just-committed booking
and correctly reports a conflict. Under `REPEATABLE READ`, step 3 would still see the pre-lock
snapshot and miss it — only the database's own unique index would catch it at that point.

**The backstop**: even if the row lock were somehow bypassed, the partial unique index itself
(`uq_one_active_booking_per_slot`) rejects a second `BOOKED` row for the same slot with a
`DuplicateKeyException`, which `BookingService` translates into the same `SlotConflictException`
(409) a normal conflict produces. This is the layer that actually guarantees correctness — the
row lock is an optimization that avoids unnecessary conflicts and retries, not the source of truth.

**`BookingFacade`** wraps `BookingService.book()` in a retry loop (up to 3 attempts, short sleep
between) that retries only a transient lock-acquisition failure
(`PessimisticLockingFailureException`) — never `SlotConflictException`, a real conflict, not a
temporary one. It's a separate Spring bean on purpose: a `@Transactional` method called from
another method on the *same* class bypasses Spring's proxy, so no transaction would actually
start on a retry. Controllers call this facade, never `BookingService` directly.

`BookingConcurrencyTest` proves this holds under real concurrency: two threads racing to book the
same slot via a `CountDownLatch` start gate — exactly one succeeds, the database ends up with
exactly one `BOOKED` row — and proves the backstop in isolation (two direct JDBC inserts, the
second throws `DuplicateKeyException`).

</details>

<details>
<summary><strong>Notification resilience, in depth</strong> — timeouts, retry, idempotency, and the two real bugs it surfaced</summary>

<br>

**Timeouts**: connect 500ms, read 1500ms, on the `RestClient`'s request factory — without this a
hung service holds a thread forever. **Retry**: up to 3 attempts, 200ms then 400ms backoff,
*only* on a timeout/connection failure or a 5xx — never on a 4xx, since that's this app's own bug
and retrying can't fix it. **Fallback**: on total failure the gateway returns a failed result and
never throws; the booking itself is never affected either way. Every attempt — and every later
resend-job cycle — carries the *same* idempotency key (`BOOKED-{id}` / `CANCELLED-{id}`), so the
mock reports `DUPLICATE` instead of ever storing the same notification twice, no matter how many
times it's retried or from which source.

Adding proper logging surfaced two gaps, both fixed:
- `GlobalExceptionHandler`'s catch-all was silently turning an unmatched route into a 500 instead
  of a 404 — a `NoResourceFoundException` landing in the same handler as everything else.
- Spring Security's own role-gate 403 (a logged-in customer hitting `/provider/**`) never reached
  `GlobalExceptionHandler` at all — it's resolved entirely inside the security filter chain. A
  small custom `AccessDeniedHandler` now logs it too, rendering the exact same page as before.

</details>

## Database design

Six tables. `services` holds the subjects a tutor teaches; `availability_slots` are the bookable
time windows; a slot is "available" when it's in the future and has no active `BOOKED`
appointment — computed at query time, never stored as a column. The double-booking guard lives
in the database itself: a partial unique index, `UNIQUE (slot_id) WHERE status = 'BOOKED'`.

![TutorSlot ER diagram](docs/er-diagram.svg)

Only keys and the fields that matter for the flow are shown (`role`, `status`, slot timing) —
every constraint is in `schema.sql`. The sixth table, `pending_notifications`, has no foreign
keys and isn't part of the booking domain — it's the durable queue behind the notification
recovery job, so it isn't pictured. Also not pictured: `appointments.reminder_sent_at`, set once
the reminder agent has handled an appointment.

## Pages and endpoints

| Method | URL | Who | Purpose |
|---|---|---|---|
| GET | `/` | public | Home — tutors and the subjects they teach |
| GET | `/slots` | public | Browse, filter (tutor/subject/date), and paginate available slots |
| GET | `/login` | public | Login form |
| POST | `/logout` | logged in | Logout |
| GET | `/customer/book?slotId=` | CUSTOMER | Book form — slot summary + optional notes |
| POST | `/customer/book` | CUSTOMER | Submit the booking, redirect to confirmation |
| GET | `/customer/appointments/{id}/confirmation` | CUSTOMER, owner | Confirmation page |
| GET | `/customer/appointments` | CUSTOMER | My appointments — Upcoming and History |
| POST | `/customer/appointments/{id}/cancel` | CUSTOMER, owner | Cancel an upcoming appointment |
| GET | `/customer/assistant` | CUSTOMER | The AI booking assistant (chat page) |
| POST | `/customer/assistant` | CUSTOMER | Send one message to the assistant |
| POST | `/customer/assistant/reset` | CUSTOMER | New conversation |
| GET | `/provider/dashboard` | PROVIDER | My slots (booked/open) + create-slot form |
| POST | `/provider/slots` | PROVIDER | Create a slot |
| POST | `/provider/slots/{id}/delete` | PROVIDER, owner | Remove a slot (only if never booked) |
| GET | `/provider/appointments` | PROVIDER | Appointments booked with me |
| POST | `/provider/reminders/run` | PROVIDER | Send due appointment reminders now |

A logged-in user hitting a page outside their role gets a `403` page, not a redirect to login. A
logged-out user hitting a protected page is sent to `/login`.

## Notifications and the mock service

Booking or cancelling sends the student a confirmation (and the reminder agent sends reminders,
with keys `REMINDER-{id}`) through a mock external service living in this same app, reachable only over HTTP + an API key (`X-Api-Key: dev-key` by default) — flip
its mode at runtime to demo an outage, no restart needed:

| Mode | Behavior |
|---|---|
| `UP` (default) | Accepts and stores the notification, `202 Accepted` |
| `DOWN` | Every request returns `503` |
| `SLOW` | Sleeps 5 seconds — longer than the client's 1500ms read timeout |

```bash
curl -X POST http://localhost:8080/mock-notify/admin/mode/DOWN -H "X-Api-Key: dev-key"
# book a slot now -- it still succeeds; the notification queues instead of being lost

curl -X POST http://localhost:8080/mock-notify/admin/mode/UP -H "X-Api-Key: dev-key"
# within notification.resend-interval-ms (30s default) the queued notification gets delivered

curl http://localhost:8080/mock-notify/notifications -H "X-Api-Key: dev-key"   # see everything received
```

## Health and metrics

```bash
curl http://localhost:8080/health                                       # {"status":"UP",...,"db":{...}}
curl http://localhost:8080/metrics/tutorslot.bookings                   # outcome=success|conflict|error
curl http://localhost:8080/metrics/tutorslot.booking.latency            # average and max booking time
curl http://localhost:8080/metrics/tutorslot.notifications              # result=sent|retry|fallback|failed
```

`/health` only reflects the database connection, deliberately — a notification outage is
visible through the `tutorslot.notifications` metric and the console logs instead. If it flipped
`/health` too, a container orchestrator would restart a perfectly healthy app over a problem
restarting it can't fix.

## Docker

```bash
docker compose up --build
```
`db` (`postgres:16`, named volume, not published on the host — 5432 is likely already your own
local Postgres) and `app` (built from the `Dockerfile`, `8080:8080`). The mock notification
service runs inside the same `app` container, so its base URL stays `localhost:8080`, not
another service name. Compose passes `GEMINI_API_KEY` from the `.env` file next to
`docker-compose.yml`; with no `.env`, the app starts with the AI features turned off.

```bash
docker compose stop db     # /health now returns 503
docker compose start db    # back to 200 UP within a few seconds
docker compose down -v     # stop everything and remove the volume
```

## Testing

```bash
./mvnw test
```
**64 tests**: unit tests (Mockito, or `MockRestServiceServer` for the HTTP gateway — no Spring
context, no database) covering booking, cancellation, slot management, and notification
retry/backoff; integration tests (`@SpringBootTest`, real local Postgres) covering the full
booking-concurrency race, the health endpoint over real HTTP, and
`BookingWithNotificationDownTest` — proving a booking survives an unreachable *and* a slow
notification service, and that a rejected booking never queues a notification at all.

The AI tests **never call Gemini** (CI has no key). They cover the tools and the confirm rule
(`AssistantToolsTest`), the chat service with a mocked chat model (`AssistantServiceTest`), the
reminder agent's AI text, template fallback, per-run cap, and resend queue
(`ReminderAgentTest`), splitting the knowledge file (`PolicySearchServiceTest`), the reminder
query on real Postgres (`AppointmentReminderQueryTest`), and `AssistantBookingGuardTest`: two
students confirm the same slot through the agent's tool at the same moment, and exactly one of
them gets it. The whole suite passes run after run with a fresh database each time.
(`src/test/resources/config/application.properties` stops the reminder job from running on its
own during tests.)

CI (`.github/workflows/ci.yml`) runs the same `./mvnw -B verify` against a real `postgres:16`
service container on every push, then confirms the Docker image still builds.

```bash
./mvnw clean package   # build a jar
```

<details>
<summary><strong>Folder structure</strong></summary>

<br>

```
TutorSlot/
├── pom.xml
├── mvnw, mvnw.cmd                     # Maven wrapper — build/run without a local Maven install
├── Dockerfile, .dockerignore          # multi-stage build; see "Docker" above
├── docker-compose.yml                 # app + db services
├── .github/workflows/ci.yml           # tests + a docker build check, on every push
├── docs/er-diagram.svg                # ER diagram used in this README
├── src/
│   ├── main/
│   │   ├── java/com/tutorslot/
│   │   │   ├── TutorSlotApplication.java      # Spring Boot entry point
│   │   │   ├── controller/                    # HomeController, SlotController, LoginController,
│   │   │   │                                  #   BookingController, AppointmentController,
│   │   │   │                                  #   ProviderController
│   │   │   ├── security/                      # SecurityConfig, AppUserDetailsService, UserPrincipal
│   │   │   ├── service/                       # ProviderService, AvailabilitySlotService,
│   │   │   │                                  #   BookingService, BookingFacade, AppointmentService,
│   │   │   │                                  #   ProviderDashboardService, CurrentUserService
│   │   │   ├── repository/                    # UserRepository, ProviderRepository, SubjectRepository,
│   │   │   │                                  #   AvailabilitySlotRepository, AppointmentRepository,
│   │   │   │                                  #   PendingNotificationRepository
│   │   │   ├── model/                         # User, Provider, Subject, AvailabilitySlot, Appointment,
│   │   │   │                                  #   PendingNotification
│   │   │   ├── dto/                           # view-facing DTOs (one per page/concern)
│   │   │   ├── exception/                     # NotFoundException, ForbiddenException, SlotConflictException
│   │   │   ├── web/                           # GlobalExceptionHandler (@ControllerAdvice)
│   │   │   ├── ai/                            # the AI agents -- AssistantTools, AssistantService,
│   │   │   │                                  #   PendingActionStore, PolicySearchService,
│   │   │   │                                  #   ReminderAgent, AiAvailability, AiConfig, GeminiText
│   │   │   ├── notification/                  # the client side of the distribution boundary --
│   │   │   │                                  #   NotificationGateway, HttpNotificationGateway,
│   │   │   │                                  #   AppointmentNotificationListener, NotificationResendJob,
│   │   │   │                                  #   NotificationAsyncConfig, NotificationClientConfig
│   │   │   └── mocknotification/               # the mock external service side --
│   │   │                                      #   MockNotificationController, InMemoryNotificationStore,
│   │   │                                      #   NotificationModeHolder (UP/DOWN/SLOW)
│   │   └── resources/
│   │       ├── schema.sql                      # all 6 tables + constraints, rerunnable
│   │       ├── seed.sql                        # sample tutors, students, subjects, slots, bookings
│   │       ├── application.properties
│   │       ├── knowledge/tutorslot-policies.md # the RAG knowledge file
│   │       └── templates/
│   │           ├── home.html, slots.html, login.html
│   │           ├── customer/                   # book.html, confirmation.html, appointments.html,
│   │           │                               #   assistant.html
│   │           ├── provider/                   # dashboard.html, appointments.html
│   │           ├── error/                      # 400, 403, 404, 409, 500
│   │           └── fragments/                  # navbar.html (role-aware, sec:authorize)
│   └── test/
│       ├── resources/config/application.properties   # test-only overrides
│       └── java/com/tutorslot/
│           ├── ai/                             # AssistantToolsTest, AssistantServiceTest,
│           │                                   #   ReminderAgentTest, PolicySearchServiceTest,
│           │                                   #   AssistantBookingGuardTest (two confirms, one slot)
│           ├── repository/RepositorySmokeTest.java, AppointmentReminderQueryTest.java
│           ├── dto/CreateSlotRequestTest.java      # Bean Validation on the slot-creation form
│           ├── web/HealthEndpointTest.java         # GET /health over real HTTP, through Spring Security
│           ├── notification/
│           │   ├── HttpNotificationGatewayTest.java   # MockRestServiceServer — retry, backoff, 4xx vs 5xx
│           │   └── NotificationResendJobTest.java
│           └── service/
│               ├── ServiceLayerSmokeTest.java
│               ├── BookingServiceTest.java         # Mockito, no Spring context
│               ├── BookingFacadeTest.java          # retry behavior + the bookings/latency meters
│               ├── AppointmentServiceCancelTest.java
│               ├── ProviderDashboardServiceTest.java
│               ├── BookingConcurrencyTest.java     # two bookings racing for one slot
│               └── BookingWithNotificationDownTest.java   # booking with notifications down or slow
```

</details>

## Known limitations

- `schema.sql` resets the database on every app restart, so the notification queue survives an
  *outage of the notification service* but not a restart of the app itself. A production version
  would stop resetting the schema.
- The assistant's chat memory, its pending proposals, and the vector store are kept in memory too,
  so a restart clears conversations and re-embeds the knowledge file (8 small embedding calls).
- The free Gemini tier is sometimes slow. A call that takes longer than 20 seconds shows "try
  again" on the chat page, and a reminder falls back to the template.
