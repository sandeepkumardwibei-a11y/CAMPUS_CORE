# CampusCore — University & Campus Management System

> **Domain:** Education & Learning  
> **Backend Stack:** Java Spring Boot · Spring Data JPA · REST API · MySQL / PostgreSQL  
> **Testing:** JUnit 5 · Mockito  
> **Phase 1 Scope:** Backend only (no frontend)

---

## Table of Contents

1. [Backend Code Flow](#backend-code-flow)
2. [Testing Strategy — JUnit & Mockito](#testing-strategy--junit--mockito)
3. [Module 4.1 — Identity & Access Management](#module-41--identity--access-management)
4. [Module 4.2 — Admissions Management](#module-42--admissions-management)
5. [Module 4.3 — Academic Registration & Course Management](#module-43--academic-registration--course-management)
6. [Module 4.4 — Attendance & Academic Progress Tracking](#module-44--attendance--academic-progress-tracking)
7. [Module 4.5 — Examination & Results Management](#module-45--examination--results-management)
8. [Module 4.6 — Fee & Scholarship Management](#module-46--fee--scholarship-management)
9. [Module 4.7 — Hostel & Campus Facilities Management](#module-47--hostel--campus-facilities-management)
10. [Module 4.8 — Notifications & Alerts](#module-48--notifications--alerts)
11. [Non-Functional Requirements](#non-functional-requirements)
12. [Assumptions & Constraints](#assumptions--constraints)

---

## Backend Code Flow

This section explains how a request travels through the Spring Boot application layers from the moment the client sends it to the moment data is returned.

```
Client (Postman / Frontend)
        │
        │  HTTP Request  (e.g. GET /api/courses/101)
        ▼
┌─────────────────────┐
│   Controller Layer  │  @RestController
│  (e.g. CourseController) │  Receives the HTTP request, validates path/query params,
│                     │  calls the Service layer, returns ResponseEntity<>
└────────┬────────────┘
         │  calls
         ▼
┌─────────────────────┐
│   Service Layer     │  @Service
│  (e.g. CourseService)   │  Contains all business logic (validations, calculations,
│                     │  status transitions). Calls the Repository layer.
└────────┬────────────┘
         │  calls
         ▼
┌─────────────────────┐
│  Repository Layer   │  @Repository 
│ (e.g. CourseRepository) │  Spring Data JPA generates SQL automatically.
│                     │  Custom queries use @Query (JPQL or native SQL).
└────────┬────────────┘
         │  SQL
         ▼
┌─────────────────────┐
│     Database        │  MySQL 
│  (e.g. course table)│  Data is fetched and returned as Entity objects.
└────────┬────────────┘
         │  Entity returned
         ▲
         │  mapped to DTO
┌─────────────────────┐
│    DTO / Model      │  Data Transfer Object — only exposes fields
│  (e.g. CourseDTO)   │  the client needs. Hides internal entity details.
└────────┬────────────┘
         │  JSON response
         ▼
     Client gets
   200 OK + JSON body
```

### Step-by-step walkthrough — Example: Get Course by ID

```
1. Client sends:   GET /api/courses/101
                   Header: Authorization: Bearer <JWT token>

2. Controller:     @GetMapping("/courses/{id}")
                   public ResponseEntity<CourseDTO> getCourse(@PathVariable Long id) {
                       CourseDTO dto = courseService.getCourseById(id);
                       return ResponseEntity.ok(dto);
                   }

3. Service:        public CourseDTO getCourseById(Long id) {
                       Course course = courseRepository.findById(id)
                           .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
                       return mapToDTO(course);   // converts Entity → DTO
                   }

4. Repository:     courseRepository.findById(id)
                   → Spring Data JPA runs:
                     SELECT * FROM course WHERE course_id = 101

5. Database:       Returns one row → mapped to Course entity

6. Service:        Entity is converted to CourseDTO (strips internal fields)

7. Controller:     Returns ResponseEntity.ok(courseDTO)
                   → HTTP 200 with JSON body

8. Client receives:
   {
     "courseId": 101,
     "courseName": "Data Structures",
     "courseCode": "CS201",
     "credits": 4,
     "facultyName": "Dr. Sharma"
   }
```

### Layer Responsibilities Summary

| Layer | Annotation | Responsibility |
|---|---|---|
| Controller | `@RestController` | Accept HTTP request, parse input, return HTTP response |
| Service | `@Service` | Business logic, validations, status checks, DTO mapping |
| Repository | `@Repository` | Database CRUD via JPA; custom `@Query` for complex queries |
| Entity | `@Entity` | Java class mapped to a DB table via JPA annotations |
| DTO | Plain class | Carries only required fields between layers and to client |
| Exception Handler | `@ControllerAdvice` | Catches exceptions and returns structured error JSON |

### Common HTTP Status Codes Used

| Status | Meaning | When used |
|---|---|---|
| 200 OK | Success | GET, successful PUT/PATCH |
| 201 Created | New resource created | POST (e.g. new application, new invoice) |
| 400 Bad Request | Validation failed | Missing/invalid fields in request body |
| 404 Not Found | Resource missing | Entity ID not found in DB |
| 409 Conflict | Duplicate resource | e.g. student already registered for course |
| 500 Internal Server Error | Unexpected failure | Unhandled exceptions |

---

## Testing Strategy — JUnit & Mockito (To Be Done)

### What is JUnit 5?
JUnit 5 is the standard Java testing framework. Each test is a method annotated with `@Test`. It checks that a method produces the expected output for a given input.

### What is Mockito?
Mockito is a mocking library. Since the **Service layer** depends on the **Repository layer**, we don't want a real database during unit tests. Mockito creates a **fake (mock)** repository that returns whatever we tell it to — making tests fast, isolated, and database-free.

### Project Test Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <!-- Includes JUnit 5 + Mockito automatically -->
</dependency>
```

---

### Test Structure

```
src/
 └── test/
      └── java/
           └── com.campuscore/
                ├── controller/
                │    └── CourseControllerTest.java   ← tests HTTP layer with MockMvc
                ├── service/
                │    └── CourseServiceTest.java      ← tests business logic with Mockito
                └── repository/
                     └── CourseRepositoryTest.java   ← tests DB queries (optional)
```

---

### Service Layer Test — Mockito Pattern

This is the most important test type. It tests business logic without touching the database.

```java
@ExtendWith(MockitoExtension.class)          // enables Mockito in JUnit 5
class CourseServiceTest {

    @Mock                                    // creates a fake repository
    private CourseRepository courseRepository;

    @InjectMocks                             // injects the mock into the real service
    private CourseService courseService;

    // ── TEST 1: Get course by valid ID ──────────────────────────────────────
    @Test
    void getCourseById_ShouldReturnCourse_WhenIdExists() {
        // ARRANGE — set up fake data
        Course mockCourse = new Course();
        mockCourse.setCourseId(101L);
        mockCourse.setCourseName("Data Structures");

        when(courseRepository.findById(101L))   // when repo is called with 101...
            .thenReturn(Optional.of(mockCourse)); // ...return our fake course

        // ACT — call the real service method
        CourseDTO result = courseService.getCourseById(101L);

        // ASSERT — check the result is correct
        assertNotNull(result);
        assertEquals("Data Structures", result.getCourseName());

        // VERIFY — confirm the repository was actually called
        verify(courseRepository, times(1)).findById(101L);
    }

    // ── TEST 2: Course not found → exception thrown ─────────────────────────
    @Test
    void getCourseById_ShouldThrowException_WhenIdNotFound() {
        when(courseRepository.findById(999L))
            .thenReturn(Optional.empty());        // simulate missing record

        assertThrows(ResourceNotFoundException.class,
            () -> courseService.getCourseById(999L));
    }

    // ── TEST 3: Create a new course ─────────────────────────────────────────
    @Test
    void createCourse_ShouldSaveAndReturnCourse() {
        CourseDTO inputDTO = new CourseDTO("Algorithms", "CS202", 3);
        Course savedCourse = new Course(102L, "Algorithms", "CS202", 3);

        when(courseRepository.save(any(Course.class)))
            .thenReturn(savedCourse);

        CourseDTO result = courseService.createCourse(inputDTO);

        assertEquals("Algorithms", result.getCourseName());
        verify(courseRepository).save(any(Course.class));
    }
}
```

---

### Controller Layer Test — MockMvc Pattern

Tests that the REST endpoints return the correct HTTP status and JSON response.

```java
@WebMvcTest(CourseController.class)          // loads only the web layer
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;                 // simulates HTTP calls

    @MockBean                                // mocks the service (not real DB)
    private CourseService courseService;

    // ── TEST: GET /api/courses/101 → 200 OK ─────────────────────────────────
    @Test
    void getCourse_ShouldReturn200_WhenCourseExists() throws Exception {
        CourseDTO dto = new CourseDTO(101L, "Data Structures", "CS201", 4);
        when(courseService.getCourseById(101L)).thenReturn(dto);

        mockMvc.perform(get("/api/courses/101"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.courseName").value("Data Structures"))
               .andExpect(jsonPath("$.credits").value(4));
    }

    // ── TEST: GET /api/courses/999 → 404 Not Found ──────────────────────────
    @Test
    void getCourse_ShouldReturn404_WhenNotFound() throws Exception {
        when(courseService.getCourseById(999L))
            .thenThrow(new ResourceNotFoundException("Course not found"));

        mockMvc.perform(get("/api/courses/999"))
               .andExpect(status().isNotFound());
    }

    // ── TEST: POST /api/courses → 201 Created ───────────────────────────────
    @Test
    void createCourse_ShouldReturn201() throws Exception {
        CourseDTO inputDTO = new CourseDTO("Algorithms", "CS202", 3);
        CourseDTO savedDTO = new CourseDTO(102L, "Algorithms", "CS202", 3);
        when(courseService.createCourse(any())).thenReturn(savedDTO);

        mockMvc.perform(post("/api/courses")
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("""
                     { "courseName": "Algorithms",
                       "courseCode": "CS202",
                       "credits": 3 }
                   """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.courseId").value(102));
    }
}
```

---

### Test Cases Per Module

| Module | Key Test Scenarios |
|---|---|
| 4.1 Identity | Register user with valid role · Reject duplicate email · Login returns JWT |
| 4.2 Admissions | Submit application · Shortlist applicant · Issue offer letter · Reject duplicate |
| 4.3 Registration | Register student for semester · Reject if max credits exceeded · Get timetable |
| 4.4 Attendance | Mark attendance · Compute attendance percent · Flag shortage below 75% |
| 4.5 Examination | Schedule exam · Submit grades · Publish result · Request re-evaluation |
| 4.6 Fee | Generate invoice · Record payment · Apply scholarship adjustment · Flag overdue |
| 4.7 Hostel | Allot room to student · Reject if room full · Book facility · Approve booking |
| 4.8 Notifications | Create notification · Mark as read · Filter by category |

---

### Mockito Cheat Sheet (for trainer reference)

| Mockito Method | What it does |
|---|---|
| `@Mock` | Creates a fake object of a class/interface |
| `@InjectMocks` | Injects all `@Mock` objects into the real class under test |
| `when(...).thenReturn(...)` | Tells the mock what to return when called |
| `when(...).thenThrow(...)` | Tells the mock to throw an exception |
| `verify(mock).method(...)` | Confirms a method was actually called |
| `verify(mock, times(n))` | Confirms a method was called exactly n times |
| `any()` / `anyLong()` | Matches any argument of that type |
| `never()` | Verifies a method was never called |

---

## Module 4.1 — Identity & Access Management

### Features
- Student, faculty, and admin registration with role-based access control (RBAC)
- Program-scoped data access and full audit logging

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive JWT token |
| GET | `/api/users/{id}` | Get user by ID |
| PUT | `/api/users/{id}/status` | Update user status |

### Entities

**User**
| Field | Notes |
|---|---|
| UserID | Primary key |
| Name | Full name |
| Role | Applicant / Student / Faculty / ExamController / Accounts / Admin |
| Email | Unique login identifier |
| Phone | Contact number |
| DepartmentID | FK to department |
| Status | Active / Alumni / Suspended / Inactive |

**AuditLog**
| Field | Notes |
|---|---|
| AuditID | Primary key |
| UserID | FK to User |
| Action | Action performed |
| Module | Module where action occurred |
| Timestamp | Date and time of action |

---

## Module 4.2 — Admissions Management

### Features
- Accept and process applications for undergraduate, postgraduate, and doctoral programs
- Manage merit-based shortlisting, document verification, and offer letter issuance

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/admissions/apply` | Submit a new application |
| GET | `/api/admissions/{id}` | Get application by ID |
| PUT | `/api/admissions/{id}/status` | Update application status (shortlist, verify, reject) |
| POST | `/api/admissions/{id}/offer` | Issue offer letter |
| GET | `/api/programs` | List all programs |

### Entities

**AdmissionApplication**
| Field | Notes |
|---|---|
| ApplicationID | Primary key |
| ApplicantName | Full name |
| Email | Contact email |
| Phone | Contact number |
| ProgramID | FK to Program |
| AcademicYear | Application year |
| QualifyingScore | Entrance / merit score |
| ApplicationDate | Date submitted |
| Status | Submitted / Shortlisted / DocumentsVerified / OfferIssued / Enrolled / Rejected / Withdrawn |

**Program**
| Field | Notes |
|---|---|
| ProgramID | Primary key |
| ProgramName | Full name |
| DepartmentID | FK to department |
| Level | UG / PG / PhD / Diploma |
| DurationYears | Length of program |
| TotalSeats | Intake capacity |
| Status | Active / Discontinued |

**OfferLetter**
| Field | Notes |
|---|---|
| OfferID | Primary key |
| ApplicationID | FK to AdmissionApplication |
| ProgramID | FK to Program |
| AcademicYear | Year of joining |
| IssuedDate | Date letter was issued |
| FeeDetailsRef | Reference to fee schedule |
| JoiningDeadline | Deadline to accept offer |
| Status | Issued / Accepted / Lapsed / Revoked |

---

## Module 4.3 — Academic Registration & Course Management

### Features
- Register students for semesters and allocate courses based on program curriculum
- Manage timetables, faculty assignments, and academic calendar events

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/registrations` | Register student for a semester |
| GET | `/api/registrations/{studentId}` | Get registrations for a student |
| GET | `/api/courses` | List all courses |
| GET | `/api/courses/{id}` | Get course by ID |
| POST | `/api/courses` | Create a new course |
| GET | `/api/timetable/{courseId}` | Get timetable for a course |

### Entities

**SemesterRegistration** · **Course** · **Timetable** — fields same as previous version

---

## Module 4.4 — Attendance & Academic Progress Tracking

### Features
- Record lecture-level attendance for each enrolled student
- Flag attendance shortages and track academic standing for progress decisions

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/attendance` | Mark attendance for a lecture |
| GET | `/api/attendance/summary/{studentId}/{courseId}` | Get attendance summary |
| GET | `/api/attendance/shortage` | List all students with shortage flag |
| GET | `/api/standing/{studentId}` | Get academic standing |

### Entities

**AttendanceRecord** · **AttendanceSummary** · **AcademicStanding** — fields same as previous version

---

## Module 4.5 — Examination & Results Management

### Features
- Schedule internal and end-semester examinations with hall allocation
- Capture faculty grade submissions and publish results with re-evaluation workflow

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/exams` | Schedule an exam |
| GET | `/api/exams/{id}` | Get exam details |
| POST | `/api/grades` | Submit grade for a student |
| PUT | `/api/grades/{id}/publish` | Publish result |
| PUT | `/api/grades/{id}/reevaluation` | Request re-evaluation |
| GET | `/api/results/{studentId}` | Get result card |

### Entities

**ExamSchedule** · **GradeRecord** · **ResultCard** — fields same as previous version

---

## Module 4.6 — Fee & Scholarship Management

### Features
- Generate semester fee invoices with configurable fee heads and due dates
- Manage scholarship awards, adjustments, and instalment-based payment plans

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/fees/invoice` | Generate fee invoice for a student |
| GET | `/api/fees/invoice/{studentId}` | Get all invoices for a student |
| POST | `/api/fees/payment` | Record a fee payment |
| GET | `/api/fees/overdue` | List overdue invoices |
| POST | `/api/scholarships` | Create scholarship record |
| PUT | `/api/scholarships/{id}/disburse` | Mark scholarship as disbursed |

### Entities

**FeeInvoice** · **FeePayment** · **Scholarship** — fields same as previous version

---

## Module 4.7 — Hostel & Campus Facilities Management

### Features
- Manage hostel blocks, room inventory, and student allotment by academic year
- Handle facility booking requests for seminar halls, labs, and sports infrastructure

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/hostel/rooms` | List all rooms with availability |
| POST | `/api/hostel/allotment` | Allot a room to a student |
| PUT | `/api/hostel/allotment/{id}/vacate` | Mark room as vacated |
| POST | `/api/facilities/booking` | Submit a facility booking request |
| PUT | `/api/facilities/booking/{id}/approve` | Approve a booking |

### Entities

**HostelRoom** · **HostelAllotment** · **FacilityBooking** — fields same as previous version

---

## Module 4.8 — Notifications & Alerts

### Features
- Admission status updates, fee due reminders, and result publication alerts for students
- Attendance shortage flags, grade submission deadlines, and exam schedule notifications for faculty

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/notifications` | Create a new notification |
| GET | `/api/notifications/{userId}` | Get all notifications for a user |
| PUT | `/api/notifications/{id}/read` | Mark notification as read |
| DELETE | `/api/notifications/{id}` | Dismiss a notification |

### Entities

**Notification**
| Field | Notes |
|---|---|
| NotificationID | Primary key |
| UserID | FK to User (recipient) |
| Message | Notification body |
| Category | Admissions / Attendance / Examination / Fee / Hostel / Academic |
| Status | Unread / Read / Dismissed |
| CreatedDate | Timestamp of creation |

---

## Non-Functional Requirements

| Requirement | Detail |
|---|---|
| Performance | Support 30,000+ concurrent users during peak periods |
| Security | RBAC, financial record encryption, full audit trails for grade actions |
| Scalability | Multi-campus, multi-program architecture with campus-scoped data partitioning |
| Availability | 99.9% uptime; exam and results modules prioritised for high availability |
| Maintainability | Configurable fee heads, grading scales, calendars, and program structures without code changes |
| Observability | Real-time attendance dashboards, fee collection pipeline metrics, exam progress tracking |

---

## Assumptions & Constraints

- Integration with national academic credential verification portals and external scholarship disbursement systems is **excluded from Phase 1**
- Fee payments are recorded via **manual or internal entry**; online payment gateway integration is deferred to Phase 2
- All notifications are **in-app only**; SMS and email alert integrations are out of scope
- **Biometric attendance** capture is deferred; attendance is recorded via faculty entry on the portal
- Fully implementable using standard **Java Spring Boot, Angular/React, and RDBMS** tooling
#   C A M P U S _ C O R E  
 