-- =============================================================
-- CampusCore: University & Campus Management System
-- MySQL Database Schema v1.0
-- =============================================================

CREATE DATABASE IF NOT EXISTS campuscore
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campuscore;

-- -------------------------------------------------------------
-- 1. USERS
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id      BIGINT          NOT NULL AUTO_INCREMENT,
    name         VARCHAR(150)    NOT NULL,
    email        VARCHAR(255)    NOT NULL UNIQUE,
    password     VARCHAR(255)    NOT NULL,
    phone        VARCHAR(20),
    role         ENUM('APPLICANT','STUDENT','FACULTY','EXAM_CONTROLLER','ACCOUNTS','ADMIN') NOT NULL,
    department_id BIGINT,
    status       ENUM('ACTIVE','INACTIVE','SUSPENDED','ALUMNI','PENDING') NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    INDEX idx_users_email   (email),
    INDEX idx_users_role    (role),
    INDEX idx_users_dept    (department_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 2. AUDIT LOG
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    log_id      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT,
    action      VARCHAR(500) NOT NULL,
    module      VARCHAR(100),
    ip_address  VARCHAR(45),
    timestamp   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    INDEX idx_audit_user      (user_id),
    INDEX idx_audit_timestamp (timestamp),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 3. PROGRAM
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS program (
    program_id      BIGINT      NOT NULL AUTO_INCREMENT,
    program_name    VARCHAR(200) NOT NULL,
    department_id   BIGINT      NOT NULL,
    level           ENUM('UG','PG','PHD','DIPLOMA') NOT NULL,
    duration_years  TINYINT     NOT NULL,
    total_seats     INT         NOT NULL DEFAULT 60,
    status          ENUM('ACTIVE','DISCONTINUED') NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (program_id),
    INDEX idx_program_dept (department_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 4. ADMISSION APPLICATION
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admission_application (
    application_id     BIGINT       NOT NULL AUTO_INCREMENT,
    applicant_name     VARCHAR(150) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    phone              VARCHAR(20),
    program_id         BIGINT       NOT NULL,
    academic_year      VARCHAR(20)  NOT NULL,
    qualifying_score   DECIMAL(6,2),
    application_date   DATE         NOT NULL,
    status             ENUM('SUBMITTED','SHORTLISTED','DOCUMENTS_VERIFIED',
                            'OFFER_ISSUED','ENROLLED','REJECTED','WITHDRAWN')
                       NOT NULL DEFAULT 'SUBMITTED',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id),
    INDEX idx_admission_email   (email),
    INDEX idx_admission_program (program_id),
    INDEX idx_admission_status  (status),
    CONSTRAINT fk_admission_program FOREIGN KEY (program_id) REFERENCES program(program_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 5. OFFER LETTER
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offer_letter (
    offer_id           BIGINT       NOT NULL AUTO_INCREMENT,
    application_id     BIGINT       NOT NULL UNIQUE,
    program_id         BIGINT       NOT NULL,
    academic_year      VARCHAR(20)  NOT NULL,
    issue_date         DATE         NOT NULL,
    joining_deadline   DATE         NOT NULL,
    scholarship_amount DECIMAL(12,2) DEFAULT 0.00,
    fee_details_ref    VARCHAR(255),
    status             ENUM('ISSUED','ACCEPTED','LAPSED','REVOKED') NOT NULL DEFAULT 'ISSUED',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (offer_id),
    INDEX idx_offer_program (program_id),
    CONSTRAINT fk_offer_application FOREIGN KEY (application_id) REFERENCES admission_application(application_id) ON DELETE CASCADE,
    CONSTRAINT fk_offer_program     FOREIGN KEY (program_id)     REFERENCES program(program_id)             ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 6. COURSE
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course (
    course_id       BIGINT      NOT NULL AUTO_INCREMENT,
    course_name     VARCHAR(200) NOT NULL,
    course_code     VARCHAR(20)  NOT NULL UNIQUE,
    program_id      BIGINT      NOT NULL,
    semester        TINYINT     NOT NULL,
    credits         TINYINT     NOT NULL,
    faculty_id      BIGINT,
    max_enrollment  INT         NOT NULL DEFAULT 60,
    status          ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (course_id),
    INDEX idx_course_program (program_id),
    INDEX idx_course_faculty (faculty_id),
    CONSTRAINT fk_course_program FOREIGN KEY (program_id) REFERENCES program(program_id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_faculty FOREIGN KEY (faculty_id) REFERENCES users(user_id)      ON DELETE SET NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 7. SEMESTER REGISTRATION
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS semester_registration (
    registration_id    BIGINT       NOT NULL AUTO_INCREMENT,
    student_id         BIGINT       NOT NULL,
    program_id         BIGINT       NOT NULL,
    academic_year      VARCHAR(20)  NOT NULL,
    semester           TINYINT      NOT NULL,
    total_credits      TINYINT      NOT NULL DEFAULT 0,
    status             ENUM('REGISTERED','CONFIRMED','WITHDRAWN') NOT NULL DEFAULT 'REGISTERED',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (registration_id),
    UNIQUE KEY uq_reg_student_semester (student_id, program_id, academic_year, semester),
    INDEX idx_reg_student  (student_id),
    INDEX idx_reg_program  (program_id),
    CONSTRAINT fk_reg_student FOREIGN KEY (student_id) REFERENCES users(user_id)    ON DELETE CASCADE,
    CONSTRAINT fk_reg_program FOREIGN KEY (program_id) REFERENCES program(program_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 8. SEMESTER REGISTRATION ↔ COURSE (Junction)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS registration_course (
    registration_id BIGINT NOT NULL,
    course_id       BIGINT NOT NULL,
    PRIMARY KEY (registration_id, course_id),
    CONSTRAINT fk_rc_registration FOREIGN KEY (registration_id) REFERENCES semester_registration(registration_id) ON DELETE CASCADE,
    CONSTRAINT fk_rc_course       FOREIGN KEY (course_id)       REFERENCES course(course_id)                      ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 9. TIMETABLE
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS timetable (
    timetable_id  BIGINT      NOT NULL AUTO_INCREMENT,
    course_id     BIGINT      NOT NULL,
    day_of_week   ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY') NOT NULL,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL,
    venue         VARCHAR(100),
    academic_year VARCHAR(20) NOT NULL,
    semester      TINYINT     NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (timetable_id),
    INDEX idx_timetable_course (course_id),
    CONSTRAINT fk_timetable_course FOREIGN KEY (course_id) REFERENCES course(course_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 10. ATTENDANCE RECORD
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_record (
    attendance_id  BIGINT   NOT NULL AUTO_INCREMENT,
    student_id     BIGINT   NOT NULL,
    course_id      BIGINT   NOT NULL,
    lecture_date   DATE     NOT NULL,
    status         ENUM('PRESENT','ABSENT','LATE','OFFICIAL_DUTY') NOT NULL,
    marked_by      BIGINT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attendance_id),
    UNIQUE KEY uq_att_student_course_date (student_id, course_id, lecture_date),
    INDEX idx_att_student (student_id),
    INDEX idx_att_course  (course_id),
    CONSTRAINT fk_att_student   FOREIGN KEY (student_id) REFERENCES users(user_id)  ON DELETE CASCADE,
    CONSTRAINT fk_att_course    FOREIGN KEY (course_id)  REFERENCES course(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_att_marker    FOREIGN KEY (marked_by)  REFERENCES users(user_id)   ON DELETE SET NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 11. ATTENDANCE SUMMARY
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_summary (
    summary_id           BIGINT          NOT NULL AUTO_INCREMENT,
    student_id           BIGINT          NOT NULL,
    course_id            BIGINT          NOT NULL,
    semester             TINYINT         NOT NULL,
    academic_year        VARCHAR(20)     NOT NULL,
    total_lectures       INT             NOT NULL DEFAULT 0,
    attended_lectures    INT             NOT NULL DEFAULT 0,
    attendance_percent   DECIMAL(5,2)    NOT NULL DEFAULT 0.00,
    shortage_flag        BOOLEAN         NOT NULL DEFAULT FALSE,
    last_updated         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (summary_id),
    UNIQUE KEY uq_att_summary (student_id, course_id, semester, academic_year),
    INDEX idx_att_sum_student (student_id),
    INDEX idx_att_sum_course  (course_id),
    CONSTRAINT fk_att_sum_student FOREIGN KEY (student_id) REFERENCES users(user_id)    ON DELETE CASCADE,
    CONSTRAINT fk_att_sum_course  FOREIGN KEY (course_id)  REFERENCES course(course_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 12. ACADEMIC STANDING
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS academic_standing (
    standing_id    BIGINT         NOT NULL AUTO_INCREMENT,
    student_id     BIGINT         NOT NULL,
    academic_year  VARCHAR(20)    NOT NULL,
    semester       TINYINT        NOT NULL,
    cgpa           DECIMAL(4,2)   NOT NULL DEFAULT 0.00,
    status         ENUM('GOOD','PROBATION','DETAINED','EXPELLED') NOT NULL DEFAULT 'GOOD',
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (standing_id),
    UNIQUE KEY uq_standing (student_id, academic_year, semester),
    INDEX idx_standing_student (student_id),
    CONSTRAINT fk_standing_student FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 13. EXAM SCHEDULE
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_schedule (
    exam_id       BIGINT      NOT NULL AUTO_INCREMENT,
    course_id     BIGINT      NOT NULL,
    semester      TINYINT     NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    exam_type     ENUM('INTERNAL','END_SEMESTER','PRACTICAL','SUPPLEMENTARY') NOT NULL,
    exam_date     DATE        NOT NULL,
    start_time    TIME        NOT NULL,
    duration_mins INT         NOT NULL DEFAULT 180,
    venue         VARCHAR(100),
    max_marks     DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    status        ENUM('SCHEDULED','CONDUCTED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exam_id),
    INDEX idx_exam_course (course_id),
    INDEX idx_exam_date   (exam_date),
    CONSTRAINT fk_exam_course FOREIGN KEY (course_id) REFERENCES course(course_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 14. GRADE RECORD
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grade_record (
    grade_id        BIGINT         NOT NULL AUTO_INCREMENT,
    exam_id         BIGINT         NOT NULL,
    student_id      BIGINT         NOT NULL,
    marks_obtained  DECIMAL(6,2)   NOT NULL DEFAULT 0.00,
    max_marks       DECIMAL(6,2)   NOT NULL DEFAULT 100.00,
    grade           VARCHAR(5),
    submitted_by_id BIGINT,
    status          ENUM('DRAFT','SUBMITTED','PUBLISHED',
                         'RE_EVALUATION_REQUESTED','REVISED') NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (grade_id),
    UNIQUE KEY uq_grade_exam_student (exam_id, student_id),
    INDEX idx_grade_student (student_id),
    INDEX idx_grade_exam    (exam_id),
    CONSTRAINT fk_grade_exam      FOREIGN KEY (exam_id)         REFERENCES exam_schedule(exam_id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_student   FOREIGN KEY (student_id)      REFERENCES users(user_id)         ON DELETE CASCADE,
    CONSTRAINT fk_grade_submitter FOREIGN KEY (submitted_by_id) REFERENCES users(user_id)         ON DELETE SET NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 15. RESULT CARD
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS result_card (
    result_id       BIGINT         NOT NULL AUTO_INCREMENT,
    student_id      BIGINT         NOT NULL,
    academic_year   VARCHAR(20)    NOT NULL,
    semester        TINYINT        NOT NULL,
    sgpa            DECIMAL(4,2)   NOT NULL DEFAULT 0.00,
    cgpa            DECIMAL(4,2)   NOT NULL DEFAULT 0.00,
    backlogs        TINYINT        NOT NULL DEFAULT 0,
    status          ENUM('DRAFT','PUBLISHED','WITHHELD') NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (result_id),
    UNIQUE KEY uq_result (student_id, academic_year, semester),
    INDEX idx_result_student (student_id),
    CONSTRAINT fk_result_student FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 16. FEE INVOICE
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_invoice (
    invoice_id           BIGINT         NOT NULL AUTO_INCREMENT,
    student_id           BIGINT         NOT NULL,
    academic_year        VARCHAR(20)    NOT NULL,
    semester             TINYINT        NOT NULL,
    tuition_fee          DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    hostel_fee           DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    library_fee          DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    lab_fee              DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    activity_fee         DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    total_amount         DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    scholarship_adjusted DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    net_payable          DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    due_date             DATE           NOT NULL,
    status               ENUM('GENERATED','PAID','PARTIALLY_PAID','OVERDUE','WAIVED')
                         NOT NULL DEFAULT 'GENERATED',
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (invoice_id),
    UNIQUE KEY uq_invoice (student_id, academic_year, semester),
    INDEX idx_invoice_student (student_id),
    INDEX idx_invoice_status  (status),
    CONSTRAINT fk_invoice_student FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 17. FEE PAYMENT
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_payment (
    payment_id    BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_id    BIGINT         NOT NULL,
    paid_amount   DECIMAL(12,2)  NOT NULL,
    payment_date  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mode          ENUM('NET_BANKING','CARD','UPI','DD','CASH','BANK_TRANSFER') NOT NULL,
    reference_no  VARCHAR(100),
    receipt_number VARCHAR(100)  UNIQUE,
    status        ENUM('RECEIVED','REVERSED') NOT NULL DEFAULT 'RECEIVED',
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    INDEX idx_payment_invoice (invoice_id),
    CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoice(invoice_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 18. SCHOLARSHIP
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarship (
    scholarship_id    BIGINT         NOT NULL AUTO_INCREMENT,
    student_id        BIGINT         NOT NULL,
    scholarship_name  VARCHAR(200)   NOT NULL,
    source_type       ENUM('GOVERNMENT','INSTITUTIONAL','EXTERNAL') NOT NULL,
    amount            DECIMAL(12,2)  NOT NULL,
    academic_year     VARCHAR(20)    NOT NULL,
    disbursed_date    DATE,
    status            ENUM('APPROVED','DISBURSED','REVOKED') NOT NULL DEFAULT 'APPROVED',
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scholarship_id),
    INDEX idx_scholarship_student (student_id),
    CONSTRAINT fk_scholarship_student FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 19. HOSTEL ROOM
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hostel_room (
    room_id        BIGINT      NOT NULL AUTO_INCREMENT,
    hostel_block   VARCHAR(50) NOT NULL,
    room_number    VARCHAR(20) NOT NULL,
    capacity       TINYINT     NOT NULL DEFAULT 2,
    occupied_count TINYINT     NOT NULL DEFAULT 0,
    room_type      ENUM('SINGLE','DOUBLE','TRIPLE') NOT NULL DEFAULT 'DOUBLE',
    status         ENUM('AVAILABLE','OCCUPIED','MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE',
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id),
    UNIQUE KEY uq_room (hostel_block, room_number),
    INDEX idx_room_status (status)
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 20. HOSTEL ALLOTMENT
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hostel_allotment (
    allotment_id   BIGINT      NOT NULL AUTO_INCREMENT,
    student_id     BIGINT      NOT NULL,
    room_id        BIGINT      NOT NULL,
    academic_year  VARCHAR(20) NOT NULL,
    checkin_date   DATE        NOT NULL,
    checkout_date  DATE,
    status         ENUM('ACTIVE','VACATED_EARLY','COMPLETED') NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (allotment_id),
    UNIQUE KEY uq_allotment_active (student_id, academic_year),
    INDEX idx_allotment_student (student_id),
    INDEX idx_allotment_room    (room_id),
    CONSTRAINT fk_allotment_student FOREIGN KEY (student_id) REFERENCES users(user_id)    ON DELETE CASCADE,
    CONSTRAINT fk_allotment_room    FOREIGN KEY (room_id)    REFERENCES hostel_room(room_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 21. FACILITY BOOKING
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facility_booking (
    booking_id     BIGINT       NOT NULL AUTO_INCREMENT,
    facility_name  VARCHAR(200) NOT NULL,
    booked_by      BIGINT       NOT NULL,
    booking_date   DATE         NOT NULL,
    start_time     TIME         NOT NULL,
    end_time       TIME         NOT NULL,
    purpose        VARCHAR(500),
    status         ENUM('REQUESTED','APPROVED','REJECTED','COMPLETED') NOT NULL DEFAULT 'REQUESTED',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (booking_id),
    INDEX idx_booking_user (booked_by),
    INDEX idx_booking_date (booking_date),
    CONSTRAINT fk_booking_user FOREIGN KEY (booked_by) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 22. NOTIFICATION
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    message         TEXT         NOT NULL,
    category        ENUM('ADMISSIONS','ATTENDANCE','EXAMINATION','FEE','HOSTEL','ACADEMIC','SYSTEM')
                    NOT NULL DEFAULT 'SYSTEM',
    status          ENUM('UNREAD','READ','DISMISSED') NOT NULL DEFAULT 'UNREAD',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    INDEX idx_notif_user   (user_id),
    INDEX idx_notif_status (status),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;
