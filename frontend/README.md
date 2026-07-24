# CampusCore — Frontend

A responsive React + Vite + Tailwind frontend for the CampusCore campus-management API. It wires **all 81 backend endpoints** across 14 domains into working screens, with JWT auth (access + refresh), a role-aware layout, and a light / dark / system theme switcher.

The backend is **not modified** — its CORS config already allows `http://localhost:5173`.

## Quick start

```bash
npm install
npm run dev
```

Open http://localhost:5173. Make sure the Spring Boot API is running on **http://localhost:8300** (context path `/api`).

The API base URL lives in `.env`:

```
VITE_API_BASE_URL=http://localhost:8300/api
```

Change it there if your backend runs elsewhere, then restart `npm run dev`.

## Stack

- **React 18** + **React Router 6**
- **Vite 5** (dev server + build)
- **Tailwind CSS 3** (dark mode via `class`)
- **axios** — one shared client with a request interceptor (attaches the Bearer token) and a response interceptor that silently refreshes the token once on a 401 and retries
- **lucide-react** — icons
- Fonts: Space Grotesk (display), Inter (body), JetBrains Mono (IDs/codes)

## How it's organized

```
src/
  lib/
    api.js         axios client, token store, 401 refresh, ApiResponse unwrap
    services.js    every endpoint, grouped by domain (AuthApi, UserApi, …)
    constants.js   enum values, status-pill colors, role-based nav
    hooks.js       useAsync + asArray (handles arrays / Spring Page / single object)
  context/         Auth, Theme, Toast providers
  components/       Layout (sidebar + topbar), ThemeToggle, ProtectedRoute, ui/
  pages/            one screen per domain (admissions & exams have detail pages)
```

## Sign in

Use any account created through your backend (e.g. register a new one on the **Create account** screen, which calls `POST /auth/register`). The login response's `role` drives which nav items appear; because the backend authorizes most routes to any authenticated user, every module is reachable so you can exercise all endpoints.

## Endpoint → screen coverage

| Domain | Endpoints | Screen |
|---|---|---|
| Auth (3) | register, login, refresh | Login, Register (refresh is automatic on 401) |
| Users (3) | me, list?role, {id}/status | Users |
| Admissions (13) | apply, evaluate, issue/accept/reject/revoke-offer, withdraw, verification-details, verify-documents, issue-admission-letter, finalize-enrollment, status, offer-details | Admissions + Application detail (stepper) |
| Programs (4) | create, all, {id}, {id}/status | Programs |
| Departments (1) | create | Departments |
| Courses (7) | create, all, {id}, program/{id}, faculty/{id}, assign-faculty, status | Courses |
| Registrations (6) | create, {id}, student/{id}, all, course/{id}, confirm | Registrations |
| Exams & Grades (11) | schedule, {id}, list, course/{id}, enter grades, publish, exam grades, student grades, compile-result, confirm registration, student results | Exams + Exam detail |
| Attendance (5) | mark, student/{id}, course/{id}/shortage, faculty/mark, faculty/{id} | Attendance |
| Fees (5) | invoices, payments, student/{id}/invoices, invoices/{id}/payments, invoices?status | Fees |
| Hostel (10) | rooms (create/list/available), applications (apply/approve/reject/pay), allotments (allot/vacate/student) | Hostel |
| Facility Bookings (4) | book, user/{id}, all, {id}/status | Facility Bookings |
| Notifications (5) | send, user/{id}, unread-count, {id}/read, user/{id}/read-all | Notifications |
| Timetable (4) | create, all, course/{id}, student/{id} | Timetable |

Total: **81 endpoints.**

## Notes

- Many list screens ask for an ID (student, course, faculty, …) because the backend exposes those reads only by ID — enter the relevant ID and load. Admissions has no "list all" route, so applications are opened by ID from the Admissions screen.
- The admission detail screen shows a pipeline stepper (SUBMITTED → … → ENROLLED) driven by the live `status` endpoint, with a card per workflow action.
- Theme choice persists in `localStorage` and defaults to your system setting.
- Time fields submit `HH:MM:SS`; if your browser omits seconds, the value still posts as entered.

## Build for production

```bash
npm run build      # outputs to dist/
npm run preview    # serve the built app locally
```
