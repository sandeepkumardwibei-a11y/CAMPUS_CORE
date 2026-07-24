# CampusCore — Project Presentation Summary
### University & Campus Management System



---

## 1. Use Cases

These are the real-world scenarios the system is built to handle — who does what and why.

| Actor | Use Case |
|---|---|
| **Prospective Student** | Submits admission application online, uploads documents, tracks application status |
| **Enrolled Student** | Registers for semester courses, views timetable, checks attendance, pays fees, downloads result card |
| **Faculty Member** | Marks daily lecture attendance, submits grades after exams, views assigned courses and office hours |
| **Examination Controller** | Schedules exams, allocates halls, publishes results, handles re-evaluation requests |
| **Accounts Executive** | Generates fee invoices, records payments, manages scholarship adjustments, follows up on overdue fees |
| **Hostel Admin** | Allots hostel rooms to students, manages room inventory, approves facility booking requests |
| **University Admin** | Configures programs, sets academic calendar, defines fee structures, manages user roles |

---

## 2. Functions (Module-wise)

The system is divided into **8 backend modules**, each handling a specific area.

### Module 4.1 — Identity & Access Management
- Register students, faculty, and admins with role-based access (RBAC)
- Secure login with JWT token authentication
- Full audit logging for every action performed in the system

### Module 4.2 — Admissions Management
- Accept applications for UG / PG / PhD / Diploma programs
- Shortlist applicants based on qualifying score
- Verify documents and issue offer letters with joining deadlines
- Track application status at every stage

### Module 4.3 — Academic Registration & Course Management
- Register students for a semester and allocate courses based on curriculum
- Manage course details — faculty assignment, credits, max enrollment
- Generate and view timetables for each course

### Module 4.4 — Attendance & Academic Progress Tracking
- Faculty marks attendance lecture-by-lecture for each enrolled student
- System auto-calculates attendance percentage per course
- Flags students with attendance below threshold (e.g. below 75%)
- Tracks CGPA and academic standing (Good / Probation / Detained / Expelled)

### Module 4.5 — Examination & Results Management
- Schedule internal, end-semester, practical, and supplementary exams with venue and timing
- Faculty submits grades; Exam Controller reviews and publishes results
- Students can request re-evaluation; revised grades are tracked with full history
- Result card generated per semester with SGPA and backlog count

### Module 4.6 — Fee & Scholarship Management
- Generate semester fee invoices with configurable fee heads (Tuition, Hostel, Library, Lab, Activity)
- Record payments with receipt number and payment mode
- Apply scholarship adjustments to net payable amount
- Track overdue invoices and partial payments

### Module 4.7 — Hostel & Campus Facilities Management
- Manage hostel blocks and room inventory (Single / Double / Triple rooms)
- Allot rooms to students per academic year with check-in and check-out tracking
- Handle booking requests for seminar halls, labs, and sports facilities
- Admin can approve, reject, or complete bookings

### Module 4.8 — Notifications & Alerts
- In-app notifications sent to students: admission updates, fee due reminders, result alerts
- In-app notifications sent to faculty: attendance shortage flags, grade submission deadlines, exam schedules
- Notifications categorized and tracked as Unread / Read / Dismissed

---

## 3. Work Flow

### Overall System Flow

```
Prospective Student
       │
       ▼
  Submits Application  ──►  Admin shortlists  ──►  Documents Verified
                                                          │
                                                          ▼
                                                   Offer Letter Issued
                                                          │
                                                          ▼
                                                   Student Enrolled
                                                          │
              ┌───────────────────────────────────────────┤
              ▼                    ▼                       ▼
   Semester Registration     Fee Invoice             Hostel Allotment
        │                    Generated                    │
        ▼                        │                        ▼
  Courses Allocated          Payment                 Room Assigned
        │                    Recorded
        ▼
  Timetable Assigned
        │
        ▼
  Attendance Marked (by Faculty)
        │
        ▼
  Exam Scheduled ──► Grades Submitted ──► Results Published
                                               │
                                               ▼
                                        Result Card Generated
```

---

### Backend Request Flow (How Data Moves in the Code)

```
CLIENT (Postman / Frontend)
        │
        │  HTTP Request  e.g. GET /api/courses/101
        ▼
   CONTROLLER  (@RestController)
   - Receives the HTTP request
   - Reads path/query parameters
   - Calls the Service layer
        │
        ▼
   SERVICE  (@Service)
   - Contains all business logic
   - Validates data (e.g. is room available? is fee overdue?)
   - Calls the Repository to fetch/save data
        │
        ▼
   REPOSITORY  (@Repository / JpaRepository)
   - Talks to the database using Spring Data JPA
   - Runs SQL queries automatically or via @Query
        │
        ▼
   DATABASE  (MySQL / PostgreSQL)
   - Stores and retrieves data
   - Returns rows as Java Entity objects
        │
        ▲  (data travels back up)
        │
   SERVICE maps Entity → DTO  (removes internal fields)
        │
        ▼
   CONTROLLER returns  ResponseEntity (HTTP 200 + JSON body)
        │
        ▼
CLIENT receives JSON response
```

### Example — Student checks attendance

```
1.  Student hits:   GET /api/attendance/summary/STU001/CS201

2.  Controller:     Reads studentId = STU001, courseId = CS201
                    Calls attendanceService.getSummary(studentId, courseId)

3.  Service:        Fetches AttendanceSummary from DB
                    Checks if AttendancePercent < 75 → sets ShortageFlag = true
                    Maps entity to DTO

4.  Repository:     SELECT * FROM attendance_summary
                    WHERE student_id = 'STU001' AND course_id = 'CS201'

5.  Response:
    {
      "courseCode": "CS201",
      "totalLectures": 40,
      "attended": 28,
      "attendancePercent": 70.0,
      "shortageFlag": true
    }
```

---

## 4. Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 21 | Core backend programming language |
| **Framework** | Spring Boot 3.3.1 | Builds REST APIs quickly with minimal configuration |
| **REST API** | Spring MVC (`@RestController`) | Exposes HTTP endpoints for all modules |
| **Database ORM** | Spring Data JPA + Hibernate | Maps Java classes to DB tables; auto-generates SQL |
| **Database** | MySQL / PostgreSQL | Stores all application data |
| **Security** | Spring Security + JWT | Role-based access control; token-based authentication |
| **Unit Testing** | JUnit 5 | Writes and runs test cases for each method |
| **Mocking** | Mockito | Fakes the database layer during testing so no real DB is needed |
| **Build Tool** | Maven | Manages dependencies (pom.xml) and builds the project |
| **API Testing** | Postman | Tests REST endpoints during development |
| **IDE** | IntelliJ IDEA / VS Code | Development environment |
| **Version Control** | Git + GitHub | Source code management and collaboration |

### Why Spring Boot?
- Auto-configures the application — no boilerplate XML needed
- Built-in embedded Tomcat server — run the app with one command
- Huge ecosystem: Security, JPA, Testing all plug in easily
- Industry standard for Java backend development

### Why JUnit + Mockito?
- **JUnit 5** runs automated test cases to verify every method works correctly
- **Mockito** mocks (fakes) the database so tests run fast without needing a real DB connection
- Together they ensure business logic is correct before deploying
- Any future code change that breaks existing logic is caught immediately by tests

---

## Project Folder Structure

```
campuscore/
├── src/
│   ├── main/
│   │   └── java/com/campuscore/
│   │       ├── controller/        ← REST endpoints (@RestController)
│   │       ├── service/           ← Business logic (@Service)
│   │       ├── repository/        ← DB access (@Repository / JpaRepository)
│   │       ├── entity/            ← DB table mappings (@Entity)
│   │       ├── dto/               ← Data Transfer Objects (API request/response)
│   │       ├── exception/         ← Custom exceptions + global error handler
│   │       └── config/            ← Security config, JWT filter
│   └── test/
│       └── java/com/campuscore/
│           ├── service/           ← JUnit + Mockito tests for service layer
│           └── controller/        ← MockMvc tests for controller layer
├── src/main/resources/
│   └── application.properties     ← DB URL, port, JPA config
└── pom.xml                        ← Maven dependencies
```

---

| Point | Answer |
|---|---|
| What does this project do? | Manages the complete student lifecycle in a university — from admission to result |
| How many modules? | 8 modules covering Admissions, Registration, Attendance, Exams, Fees, Hostel, Notifications |
| What is the backend? | Java Spring Boot REST API with Spring Data JPA |
| What database? | MySQL / PostgreSQL (Relational DB) |
| How is security handled? | JWT tokens + Spring Security with Role-Based Access Control |
| How is it tested? | JUnit 5 for unit tests, Mockito to mock the repository layer |
| What phase is complete? | Phase 1 — full backend with all 8 modules and test cases |
| What is deferred? | Online payment gateway, SMS/email alerts, biometric attendance (Phase 2) |
