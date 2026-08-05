-- =============================================================
-- CampusCore Seed Data
-- Only the System Admin account is seeded.
-- =============================================================

INSERT IGNORE INTO users (user_id, name, email, password, phone, role, department_id, status) VALUES
(1, 'System Admin', 'admin@campuscore.com', '$2a$12$R3.bQkIt.6CsC/YguhrltuqLQ7aMxLLx7r3RmdtHnrcqlj29h0Gcm', '1234567890', 'ADMIN', NULL, 'ACTIVE');
