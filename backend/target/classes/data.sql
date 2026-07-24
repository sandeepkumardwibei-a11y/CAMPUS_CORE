-- =============================================================
-- CampusCore Seed Data
-- =============================================================

-- 1. Seed Programs
INSERT IGNORE INTO program (program_id, program_name, level, duration_years, total_seats, status) VALUES
(1, 'B.Tech Computer Science', 'UG', 4, 120, 'ACTIVE'),
(2, 'M.Tech Data Science', 'PG', 2, 60, 'ACTIVE');

-- 2. Seed Users
INSERT IGNORE INTO users (user_id, name, email, password, phone, role, department_id, status) VALUES
(1, 'System Admin', 'admin@campuscore.com', '$2a$12$R3.bQkIt.6CsC/YguhrltuqLQ7aMxLLx7r3RmdtHnrcqlj29h0Gcm', '1234567890', 'ADMIN', NULL, 'ACTIVE'),
(2, 'Exam Controller', 'exam@campuscore.com', '$2a$12$XGzxswA2RYEMfZdaChyHw.kjMwPYr7uflIO9yLE010qZ1JZp/frQ.', '1234567891', 'EXAM_CONTROLLER', NULL, 'ACTIVE'),
(3, 'Accounts Head', 'accounts@campuscore.com', '$2a$12$jVHwQ/S4BeSiXHpBzsrPQOdn0W6C911b0jwcsfUDX3ZzDeOOj2r.W', '1234567892', 'ACCOUNTS', NULL, 'ACTIVE'),
(4, 'Dr. Alan Turing', 'turing@campuscore.com', '$2a$12$q6KZ6rLZzLRdvkX9jdZPLeLX2Sx3/vUv9AvF7t5bxOFVU8QN1Pr36', '1234567893', 'FACULTY', 1, 'ACTIVE'),
(5, 'Khushal Kumar', 'student1@campuscore.com', '$2a$12$PV6UHCMSpKOFwg20bK3/hu4NkBY4txNpiZTi5gGtH/qVA5oSnPYEu', '1234567894', 'STUDENT', 1, 'ACTIVE'),
(6,'Hostel Admin','hosteladmin@campuscore.com','$2a$12$R3.bQkIt.6CsC/YguhrltuqLQ7aMxLLx7r3RmdtHnrcqlj29h0Gcm','1234567895','HOSTEL_ADMIN',NULL,'ACTIVE');


-- 3. Seed Courses
INSERT IGNORE INTO course (course_id, course_name, course_code, program_id, semester, credits, faculty_id, max_enrollment, status) VALUES
(1, 'Data Structures & Algorithms', 'CS201', 1, 3, 4, 4, 60, 'ACTIVE'),
(2, 'Database Management Systems', 'CS202', 1, 3, 4, 4, 60, 'ACTIVE'),
(3, 'Operating Systems', 'CS203', 1, 3, 4, 4, 60, 'ACTIVE');

-- 4. Seed Hostel Rooms
INSERT IGNORE INTO hostel_room (room_id, hostel_block, room_number, capacity, occupied_count, room_type, status) VALUES
(1, 'A-Block', '101', 2, 0, 'DOUBLE', 'AVAILABLE'),
(2, 'A-Block', '102', 2, 0, 'DOUBLE', 'AVAILABLE'),
(3, 'B-Block', '201', 1, 0, 'SINGLE', 'AVAILABLE');
