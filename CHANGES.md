# CampusCore — Change Set (12 items)

This document summarizes the changes made across the Spring Boot backend and
React (Vite) frontend. The project could not be compiled in the editing
environment (no network for Maven/npm), so please run a local build:

    # backend
    cd backend && mvn clean package

    # frontend
    cd frontend && npm install && npm run dev

Apply the DB migration once against the `campuscore` database:

    mysql campuscore < database/migration_relationships.sql

---

## 1. Input cursor / alignment
`frontend/src/index.css` — the shared `.field` style now sets `caret-color`,
`line-height`, `min-height`, vertical alignment, and hides native number
spinners. This fixes the Student ID box (and every other input) where the
typed value/caret was not visible.

## 2. Department creation — name only
Removed `programId` from the Department entity, DTO, service, and repository.
`frontend/src/pages/Departments.jsx` no longer shows a Program ID field/column.

## 3. Program creation — department dropdown (one dept → many programs)
`Program` now has a `departmentId`. Program creation takes a single department
(validated) instead of a course list. `Programs.jsx` uses a single-select
department dropdown and shows the department column.

## 4. Course creation — program multi-select (many-to-many)
`Course` gains a `programIds` collection (new `course_programs` join table).
A course can belong to multiple programs and vice-versa. `Courses.jsx` uses a
program multi-select and lists all linked program names.

## 5. Admission — department then program (filtered)
`Admissions.jsx` shows a department first; the program dropdown only lists
programs under the chosen department. `AdmissionService` validates the
program-belongs-to-department rule.

## 6. Role-based display
`NAV`/`PERMS` in `constants.js` updated. Admissions removed from the STUDENT
nav. Page action buttons are gated by `can(role, action)`.

## 7. Academic standing (auto-ranking)
New backend `AcademicStandingService`/`Controller`/`DTO`:
CGPA > 9 EXCELLENT, 8–9 GOOD, 5–8 AVERAGE, < 5 POOR (+ remark). Admin & faculty
see all students; a student sees only their own (enforced by `@PreAuthorize`).
New page `AcademicStanding.jsx` at `/academic-standing`.

## 8. Holidays (animated, icons)
Hardcoded 2026 Indian holidays: backend `HolidayCalendar.java` and frontend
`lib/holidays.js` (themed icons, e.g. Christmas 🎄). New animated calendar page
`Holidays.jsx` at `/holidays`, visible to all except applicants. Exams cannot
be scheduled on holidays (`ExamService`).

## 9. Faculty dropdowns everywhere
New reusable `components/ui/FacultySelect.jsx` (emits userId, or name via
`byName`). Wired into Courses (create + assign), Attendance (mark + view),
and ExamDetail grade entry.

## 10. Audit & module logs (admin only)
New backend `LogController` — `/logs/audit` and `/logs/module` (ADMIN only),
returning flat serialization-safe views. New admin-only page `Logs.jsx` at
`/logs` with two tabs.

## 11. Interactive colourful timetable + break rules
`Timetable.jsx` grid view (days × hours, per-course colours, break bands).
College hours 08:00–16:00; breaks 10:00–10:15, 12:00–13:00 (lunch),
14:45–15:00. No scheduling during breaks — enforced client-side and in
`TimetableService` (and the same guard added to `ExamService`).

## 12. Hostel payment QR
Replaced `frontend/public/hostel-payment-qr.png` with the provided QR.

---

## New / changed files (high level)

Backend:
- entity: Department, Program, Course (relationship changes)
- dto: DepartmentDto, ProgramDto, CourseDto, **AcademicStandingDto** (new)
- service: DepartmentService, ProgramService, CourseService, AdmissionService,
  ExamService, TimetableService, **AcademicStandingService** (new),
  **HolidayCalendar** (new)
- controller: **AcademicStandingController** (new), **LogController** (new)
- repository: DepartmentRepository, ProgramRepository, CourseRepository,
  UserRepository (findByRole)

Frontend:
- lib: services.js, constants.js, **holidays.js** (new)
- components/ui: **FacultySelect.jsx** (new)
- pages: Departments, Programs, Courses, admissions/Admissions, Timetable,
  Attendance, exams/Exams, exams/ExamDetail,
  **AcademicStanding**, **Holidays**, **Logs** (new)
- index.css, App.jsx
- public/hostel-payment-qr.png (replaced)

Database:
- **database/migration_relationships.sql** (new)
