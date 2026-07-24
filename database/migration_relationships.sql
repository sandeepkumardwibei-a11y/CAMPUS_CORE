-- =============================================================
-- CampusCore migration — relationship changes (items 2, 3, 4)
-- Run against the `campuscore` database.
-- Safe to re-run: uses IF EXISTS / IF NOT EXISTS where possible.
-- =============================================================

-- -------------------------------------------------------------
-- ITEM 2: Department no longer references a program.
-- The department is now standalone (name only). A program points
-- to its department instead (one department -> many programs).
-- -------------------------------------------------------------

-- Drop the old FK/column from department if they exist.
-- (MySQL doesn't support "DROP COLUMN IF EXISTS" before 8.0.29, so guard manually.)
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'department' AND COLUMN_NAME = 'program_id');
SET @sql := IF(@col > 0, 'ALTER TABLE department DROP COLUMN program_id', 'SELECT "department.program_id already removed"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Ensure department has a status column (kept from the entity)
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'department' AND COLUMN_NAME = 'status');
SET @sql := IF(@col = 0,
    "ALTER TABLE department ADD COLUMN status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE'",
    'SELECT "department.status present"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- ITEM 3: program.department_id (one dept -> many programs)
-- The schema already declares department_id NOT NULL; make sure
-- an index/relationship is present. If your existing data has
-- programs without a department, relax to NULL first, backfill,
-- then re-tighten as desired.
-- -------------------------------------------------------------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'program' AND COLUMN_NAME = 'department_id');
SET @sql := IF(@col = 0,
    'ALTER TABLE program ADD COLUMN department_id BIGINT NULL',
    'SELECT "program.department_id present"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Allow NULL during transition so existing rows without a dept don't block startup.
ALTER TABLE program MODIFY COLUMN department_id BIGINT NULL;

-- -------------------------------------------------------------
-- ITEM 4: many-to-many between program and course.
-- New join table course_programs (course_id, program_id).
-- The legacy course.program_id stays as the optional "primary"
-- program, but is relaxed to NULL so a course can span programs.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_programs (
    course_id  BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    PRIMARY KEY (course_id, program_id),
    INDEX idx_cp_course  (course_id),
    INDEX idx_cp_program (program_id),
    CONSTRAINT fk_cp_course  FOREIGN KEY (course_id)  REFERENCES course(course_id)   ON DELETE CASCADE,
    CONSTRAINT fk_cp_program FOREIGN KEY (program_id) REFERENCES program(program_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Relax the legacy single-program FK so a standalone / multi-program course is allowed.
ALTER TABLE course MODIFY COLUMN program_id BIGINT NULL;
ALTER TABLE course MODIFY COLUMN semester   TINYINT NULL;

-- Backfill the join table from any existing single-program links.
INSERT IGNORE INTO course_programs (course_id, program_id)
SELECT course_id, program_id FROM course WHERE program_id IS NOT NULL;

-- -------------------------------------------------------------
-- Clean up the old program_courses ElementCollection table if it
-- exists (it was owned by Program before the refactor).
-- -------------------------------------------------------------
DROP TABLE IF EXISTS program_courses;
