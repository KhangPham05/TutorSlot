-- TutorSlot schema (Milestone 1)
-- Rerunnable: drops all tables (reverse dependency order) before recreating them.

DROP TABLE IF EXISTS appointments CASCADE;
DROP TABLE IF EXISTS availability_slots CASCADE;
DROP TABLE IF EXISTS services CASCADE;
DROP TABLE IF EXISTS providers CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- users: every person who can log in, either a customer (student) or a provider (tutor).
CREATE TABLE users (
    user_id        BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL, -- placeholder text in M1, real BCrypt hash in M2
    full_name      VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'PROVIDER'))
);

-- providers: one row per tutor, 1:1 with a PROVIDER user.
CREATE TABLE providers (
    provider_id  BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    title        VARCHAR(100), -- e.g. "Math & CS Tutor"
    bio          TEXT,
    CONSTRAINT uq_providers_user_id UNIQUE (user_id),
    CONSTRAINT fk_providers_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- services: a subject taught by a specific tutor (e.g. "Calculus I" by Alice).
-- uq_services_service_provider exists only so availability_slots can FK against the
-- (service_id, provider_id) pair and guarantee a slot's service really belongs to its provider.
CREATE TABLE services (
    service_id        BIGSERIAL PRIMARY KEY,
    provider_id        BIGINT NOT NULL,
    name               VARCHAR(100) NOT NULL,
    description        TEXT,
    duration_minutes   INT NOT NULL,
    CONSTRAINT fk_services_provider FOREIGN KEY (provider_id) REFERENCES providers (provider_id),
    CONSTRAINT ck_services_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT uq_services_provider_name UNIQUE (provider_id, name),
    CONSTRAINT uq_services_service_provider UNIQUE (service_id, provider_id)
);

-- availability_slots: a single bookable time window a tutor has opened up for one of their services.
CREATE TABLE availability_slots (
    slot_id      BIGSERIAL PRIMARY KEY,
    provider_id  BIGINT NOT NULL,
    service_id   BIGINT NOT NULL,
    start_time   TIMESTAMP NOT NULL,
    end_time     TIMESTAMP NOT NULL,
    CONSTRAINT fk_slots_provider FOREIGN KEY (provider_id) REFERENCES providers (provider_id),
    CONSTRAINT fk_slots_service_provider FOREIGN KEY (service_id, provider_id)
        REFERENCES services (service_id, provider_id),
    CONSTRAINT uq_slots_provider_start UNIQUE (provider_id, start_time),
    CONSTRAINT ck_slots_end_after_start CHECK (end_time > start_time)
);

CREATE INDEX idx_slots_start_time ON availability_slots (start_time);

-- appointments: a customer's booking against one slot. Kept as history even after cancellation.
CREATE TABLE appointments (
    appointment_id  BIGSERIAL PRIMARY KEY,
    slot_id         BIGINT NOT NULL,
    customer_id     BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    cancelled_at    TIMESTAMP NULL,
    CONSTRAINT fk_appointments_slot FOREIGN KEY (slot_id) REFERENCES availability_slots (slot_id),
    CONSTRAINT fk_appointments_customer FOREIGN KEY (customer_id) REFERENCES users (user_id),
    CONSTRAINT ck_appointments_status CHECK (status IN ('BOOKED', 'CANCELLED')),
    CONSTRAINT ck_appointments_cancelled_at_consistency
        CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

-- Double-booking guard: at most one BOOKED appointment per slot. Cancelled appointments don't
-- count, so a slot freed up by a cancellation can be booked again. Enforced by Postgres itself,
-- so two simultaneous booking requests for the same slot cannot both succeed.
CREATE UNIQUE INDEX uq_one_active_booking_per_slot
    ON appointments (slot_id)
    WHERE status = 'BOOKED';
