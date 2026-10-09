-- TutorSlot sample data (Milestone 1 + Milestone 2)
-- Loaded after schema.sql. Slot times are relative to CURRENT_DATE so they are always in the
-- future (or, for the one past slot below, always in the past), no matter when this script runs.

-- 3 tutors (PROVIDER users) + 3 students (CUSTOMER users).
-- Every demo account shares the password "password123" (BCryptPasswordEncoder, strength 10).
INSERT INTO users (email, password_hash, full_name, role) VALUES
    ('alice.tutor@sjsu.edu',  '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Alice Nguyen',  'PROVIDER'),
    ('bob.tutor@sjsu.edu',    '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Bob Martinez',  'PROVIDER'),
    ('carla.tutor@sjsu.edu',  '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Carla Osei',    'PROVIDER'),
    ('balaji.tutor@sjsu.edu', '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Balaji Venkatraman', 'PROVIDER'),
    ('dan.student@sjsu.edu',  '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Dan Kim',       'CUSTOMER'),
    ('erin.student@sjsu.edu', '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Erin Patel',    'CUSTOMER'),
    ('finn.student@sjsu.edu', '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Finn O''Brien', 'CUSTOMER'),
    ('hoangpham123@sjsu.edu', '$2a$10$2C9mHY3KCjWHLHcVChNBkO1IuEqDThM1T/uvSt5tM780egGQVXu6K', 'Hoang Pham',    'CUSTOMER');

INSERT INTO providers (user_id, title, bio) VALUES
    ((SELECT user_id FROM users WHERE email = 'alice.tutor@sjsu.edu'), 'Math & CS Tutor',
        'CS senior specializing in calculus and intro programming.'),
    ((SELECT user_id FROM users WHERE email = 'bob.tutor@sjsu.edu'),   'CS Tutor',
        'Teaching assistant for data structures and algorithms.'),
    ((SELECT user_id FROM users WHERE email = 'carla.tutor@sjsu.edu'), 'Physics Tutor',
        'Physics grad student, tutors intro mechanics and E&M.'),
    ((SELECT user_id FROM users WHERE email = 'balaji.tutor@sjsu.edu'), 'Database Tutor',
        'Backend engineer, tutors relational database design and SQL.');

-- 2-3 subjects per tutor
INSERT INTO services (provider_id, name, description, duration_minutes) VALUES
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        'Calculus I', 'Limits, derivatives, and intro integration.', 60),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        'Intro to Java', 'Java syntax, OOP basics, and debugging.', 60),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        'Data Structures', 'Lists, trees, hash maps, and complexity analysis.', 60),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        'Algorithms', 'Sorting, graph traversal, and dynamic programming.', 60),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        'Physics I', 'Kinematics, Newton''s laws, and energy.', 60),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        'Physics II', 'Electricity and magnetism fundamentals.', 60),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'balaji.tutor@sjsu.edu'),
        'Database Systems', 'Relational design, normalization, and SQL query tuning.', 60);

-- ~18 slots spread over the next 7 days, 60-minute sessions during daytime hours (10am-4pm),
-- plus one slot in the past (see below) for the M2 COMPLETED-status demo.
INSERT INTO availability_slots (provider_id, service_id, start_time, end_time) VALUES
    -- Alice: Calculus I
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Calculus I'),
        CURRENT_DATE + INTERVAL '1 day' + TIME '10:00', CURRENT_DATE + INTERVAL '1 day' + TIME '11:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Calculus I'),
        CURRENT_DATE + INTERVAL '2 day' + TIME '13:00', CURRENT_DATE + INTERVAL '2 day' + TIME '14:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Calculus I'),
        CURRENT_DATE + INTERVAL '4 day' + TIME '15:00', CURRENT_DATE + INTERVAL '4 day' + TIME '16:00'),
    -- Alice: Intro to Java
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Intro to Java'),
        CURRENT_DATE + INTERVAL '1 day' + TIME '14:00', CURRENT_DATE + INTERVAL '1 day' + TIME '15:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Intro to Java'),
        CURRENT_DATE + INTERVAL '3 day' + TIME '10:00', CURRENT_DATE + INTERVAL '3 day' + TIME '11:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Intro to Java'),
        CURRENT_DATE + INTERVAL '6 day' + TIME '11:00', CURRENT_DATE + INTERVAL '6 day' + TIME '12:00'),
    -- Bob: Data Structures
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Data Structures'),
        CURRENT_DATE + INTERVAL '1 day' + TIME '11:00', CURRENT_DATE + INTERVAL '1 day' + TIME '12:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Data Structures'),
        CURRENT_DATE + INTERVAL '2 day' + TIME '10:00', CURRENT_DATE + INTERVAL '2 day' + TIME '11:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Data Structures'),
        CURRENT_DATE + INTERVAL '5 day' + TIME '13:00', CURRENT_DATE + INTERVAL '5 day' + TIME '14:00'),
    -- Bob: Algorithms
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Algorithms'),
        CURRENT_DATE + INTERVAL '2 day' + TIME '15:00', CURRENT_DATE + INTERVAL '2 day' + TIME '16:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Algorithms'),
        CURRENT_DATE + INTERVAL '4 day' + TIME '10:00', CURRENT_DATE + INTERVAL '4 day' + TIME '11:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Algorithms'),
        CURRENT_DATE + INTERVAL '6 day' + TIME '14:00', CURRENT_DATE + INTERVAL '6 day' + TIME '15:00'),
    -- Carla: Physics I
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Physics I'),
        CURRENT_DATE + INTERVAL '1 day' + TIME '13:00', CURRENT_DATE + INTERVAL '1 day' + TIME '14:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Physics I'),
        CURRENT_DATE + INTERVAL '3 day' + TIME '14:00', CURRENT_DATE + INTERVAL '3 day' + TIME '15:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Physics I'),
        CURRENT_DATE + INTERVAL '5 day' + TIME '10:00', CURRENT_DATE + INTERVAL '5 day' + TIME '11:00'),
    -- Carla: Physics II
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Physics II'),
        CURRENT_DATE + INTERVAL '2 day' + TIME '11:00', CURRENT_DATE + INTERVAL '2 day' + TIME '12:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Physics II'),
        CURRENT_DATE + INTERVAL '4 day' + TIME '13:00', CURRENT_DATE + INTERVAL '4 day' + TIME '14:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Physics II'),
        CURRENT_DATE + INTERVAL '7 day' + TIME '15:00', CURRENT_DATE + INTERVAL '7 day' + TIME '16:00'),
    -- Alice: Calculus I, one slot in the past, so there's a BOOKED appointment the M2 "mark
    -- past bookings as COMPLETED" logic has something to act on.
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Calculus I'),
        CURRENT_DATE - INTERVAL '2 day' + TIME '10:00', CURRENT_DATE - INTERVAL '2 day' + TIME '11:00'),
    -- Balaji: Database Systems
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'balaji.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Database Systems'),
        CURRENT_DATE + INTERVAL '2 day' + TIME '14:00', CURRENT_DATE + INTERVAL '2 day' + TIME '15:00'),
    ((SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'balaji.tutor@sjsu.edu'),
        (SELECT service_id FROM services WHERE name = 'Database Systems'),
        CURRENT_DATE + INTERVAL '5 day' + TIME '11:00', CURRENT_DATE + INTERVAL '5 day' + TIME '12:00');

-- 3 BOOKED (future) + 1 CANCELLED appointment, so GET /slots visibly hides the booked ones.
-- A 4th BOOKED appointment, on the past slot above, is added further down.
-- Dan books Alice's Calculus I slot (day 1, 10:00).
INSERT INTO appointments (slot_id, customer_id, status, notes) VALUES
    ((SELECT slot_id FROM availability_slots
        WHERE provider_id = (SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu')
          AND start_time = CURRENT_DATE + INTERVAL '1 day' + TIME '10:00'),
     (SELECT user_id FROM users WHERE email = 'dan.student@sjsu.edu'), 'BOOKED', 'First calculus session.');

-- Erin books Bob's Data Structures slot (day 1, 11:00).
INSERT INTO appointments (slot_id, customer_id, status, notes) VALUES
    ((SELECT slot_id FROM availability_slots
        WHERE provider_id = (SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'bob.tutor@sjsu.edu')
          AND start_time = CURRENT_DATE + INTERVAL '1 day' + TIME '11:00'),
     (SELECT user_id FROM users WHERE email = 'erin.student@sjsu.edu'), 'BOOKED', 'Need help with linked lists.');

-- Finn books Carla's Physics I slot (day 1, 13:00).
INSERT INTO appointments (slot_id, customer_id, status, notes) VALUES
    ((SELECT slot_id FROM availability_slots
        WHERE provider_id = (SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'carla.tutor@sjsu.edu')
          AND start_time = CURRENT_DATE + INTERVAL '1 day' + TIME '13:00'),
     (SELECT user_id FROM users WHERE email = 'finn.student@sjsu.edu'), 'BOOKED', NULL);

-- Dan then cancels Alice's Intro to Java slot (day 3, 10:00) he had booked earlier.
INSERT INTO appointments (slot_id, customer_id, status, notes, cancelled_at) VALUES
    ((SELECT slot_id FROM availability_slots
        WHERE provider_id = (SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu')
          AND start_time = CURRENT_DATE + INTERVAL '3 day' + TIME '10:00'),
     (SELECT user_id FROM users WHERE email = 'dan.student@sjsu.edu'), 'CANCELLED', 'Schedule conflict came up.', NOW());

-- Finn booked Alice's past Calculus I slot. Still BOOKED here on purpose: the seed inserts
-- directly, bypassing BookingService, so the M2 appointments-list logic is what flips this to
-- COMPLETED (status BOOKED + slot.end_time < NOW()) the first time that page is loaded.
INSERT INTO appointments (slot_id, customer_id, status, notes) VALUES
    ((SELECT slot_id FROM availability_slots
        WHERE provider_id = (SELECT provider_id FROM providers p JOIN users u ON u.user_id = p.user_id WHERE u.email = 'alice.tutor@sjsu.edu')
          AND start_time = CURRENT_DATE - INTERVAL '2 day' + TIME '10:00'),
     (SELECT user_id FROM users WHERE email = 'finn.student@sjsu.edu'), 'BOOKED', 'Past session, should show as completed.');
