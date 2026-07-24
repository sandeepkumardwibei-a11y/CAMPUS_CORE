import {
  LayoutDashboard, GraduationCap, Users, BookOpen, Building2, Layers,
  ClipboardList, FileSpreadsheet, CalendarCheck, Wallet, BedDouble,
  CalendarClock, Bell, CalendarDays, UserCog, Award, PartyPopper, ScrollText,
} from 'lucide-react'

// ---- Enum values (mirrors backend) ----
export const ROLES = ['APPLICANT', 'STUDENT', 'FACULTY', 'EXAM_CONTROLLER', 'ACCOUNTS', 'ADMIN', 'HOSTEL_ADMIN']
export const USER_STATUS = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ALUMNI', 'PENDING']
export const PROGRAM_LEVELS = ['UG', 'PG', 'PHD', 'DIPLOMA']
export const PROGRAM_STATUS = ['ACTIVE', 'DISCONTINUED']
export const COURSE_STATUS = ['ACTIVE', 'INACTIVE']
export const EXAM_TYPES = ['INTERNAL', 'END_SEMESTER', 'PRACTICAL', 'SUPPLEMENTARY', 'MID']
export const ATTENDANCE_STATUS = ['PRESENT', 'ABSENT', 'LATE', 'OFFICIAL_DUTY']
export const PAYMENT_MODES = ['NET_BANKING', 'CARD', 'UPI', 'DD', 'CASH', 'BANK_TRANSFER']
export const INVOICE_STATUS = ['GENERATED', 'PAID', 'PARTIALLY_PAID', 'OVERDUE', 'WAIVED']
export const ROOM_TYPES = ['SINGLE', 'DOUBLE', 'TRIPLE']
export const BOOKING_STATUS = ['REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED']
export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']
export const NOTIF_CATEGORIES = ['INFO', 'ALERT', 'REMINDER', 'ACADEMIC', 'FINANCE']
export const ADMISSION_STATUS = [
  'SUBMITTED', 'SHORTLISTED', 'NOT_SHORTLISTED', 'OFFER_ISSUED', 'OFFER_ACCEPTED',
  'DOCUMENTS_VERIFIED', 'ADMISSION_LETTER_ISSUED', 'ENROLLED', 'REJECTED', 'WITHDRAWN', 'REVOKED',
]

// Admission pipeline (the 8-stage happy path, for the stepper)
export const ADMISSION_PIPELINE = [
  'SUBMITTED', 'SHORTLISTED', 'OFFER_ISSUED', 'OFFER_ACCEPTED',
  'DOCUMENTS_VERIFIED', 'ADMISSION_LETTER_ISSUED', 'ENROLLED',
]

// ---- Status pill colors ----
// green=good, amber=in-progress, rose=terminal-bad, indigo=neutral-active, slate=inactive
export const STATUS_TONE = {
  // shared / good
  ACTIVE: 'green', APPROVED: 'green', PAID: 'green', ENROLLED: 'green', PUBLISHED: 'green',
  CONFIRMED: 'green', PRESENT: 'green', RECEIVED: 'green', DISBURSED: 'green', COMPLETED: 'green',
  DOCUMENTS_VERIFIED: 'green', OFFER_ACCEPTED: 'green', GOOD: 'green', ADMISSION_LETTER_ISSUED: 'green',
  // in-progress / warning
  PENDING: 'amber', GENERATED: 'amber', PARTIALLY_PAID: 'amber', REQUESTED: 'amber',
  SUBMITTED: 'amber', SHORTLISTED: 'amber', OFFER_ISSUED: 'amber', LATE: 'amber',
  DRAFT: 'amber', SCHEDULED: 'amber', REGISTERED: 'amber', PROBATION: 'amber', OVERDUE: 'amber',
  // bad / terminal
  REJECTED: 'rose', REVOKED: 'rose', WITHDRAWN: 'rose', SUSPENDED: 'rose', ABSENT: 'rose',
  NOT_SHORTLISTED: 'rose', CANCELLED: 'rose', DETAINED: 'rose', EXPELLED: 'rose', REVERSED: 'rose',
  DISCONTINUED: 'rose', WITHHELD: 'rose',
  // inactive / neutral
  INACTIVE: 'slate', ALUMNI: 'slate', VACATED_EARLY: 'slate', DISMISSED: 'slate', WAIVED: 'slate',
}

export const toneClasses = {
  green: 'bg-emerald-500/12 text-emerald-500 ring-emerald-500/25',
  amber: 'bg-amber-500/12 text-amber-500 ring-amber-500/25',
  rose: 'bg-rose-500/12 text-rose-500 ring-rose-500/25',
  slate: 'bg-slate-500/12 text-slate-400 ring-slate-500/25',
  indigo: 'bg-indigo-500/12 text-indigo-400 ring-indigo-500/25',
}

// ---- Navigation (module -> allowed roles) ----
// Backend authorizes most routes to any authenticated user, so every module is
// reachable; roles here drive sensible default visibility. ADMIN sees everything.
// ---- Permission matrix (mirrors backend @PreAuthorize exactly) ----
// 'AUTH' = any authenticated user. Otherwise, the listed roles only.
// Owner-scoped backend rules (e.g. "or #studentId == principal.userId") are
// represented by including the owning role (STUDENT/FACULTY) here.
export const PERMS = {
  // Users
  'users.list': ['ADMIN', 'FACULTY', 'EXAM_CONTROLLER', 'ACCOUNTS'],
  'users.updateStatus': ['ADMIN'],
  // Admissions
  'adm.apply': ['APPLICANT'],
  'adm.evaluate': ['ADMIN'],
  'adm.issueOffer': ['ADMIN'],
  'adm.acceptOffer': ['APPLICANT'],
  'adm.rejectOffer': ['APPLICANT'],
  'adm.withdraw': ['APPLICANT'],
  'adm.revokeOffer': ['ADMIN'],
  'adm.verifyDocuments': ['ADMIN'],
  'adm.issueAdmissionLetter': ['ADMIN'],
  'adm.finalizeEnrollment': ['APPLICANT', 'STUDENT'],
  'adm.view': 'AUTH', // status / offer-details / verification-details
  // Programs
  'prog.create': ['ADMIN'],
  'prog.updateStatus': ['ADMIN'],
  'prog.read': 'AUTH',
  // Departments
  'dept.create': ['ADMIN'],
  // Courses
  'course.create': ['ADMIN'],
  'course.assignFaculty': ['ADMIN'],
  'course.updateStatus': ['ADMIN'],
  'course.read': 'AUTH',
  // Registrations
  'reg.create': ['STUDENT'],
  'reg.byStudent': ['ADMIN', 'STUDENT'],
  'reg.all': ['ADMIN', 'EXAM_CONTROLLER'],
  'reg.byCourse': ['ADMIN', 'FACULTY', 'EXAM_CONTROLLER'],
  'reg.byId': ['ADMIN', 'STUDENT', 'FACULTY', 'EXAM_CONTROLLER'],
  'reg.confirm': ['ADMIN', 'EXAM_CONTROLLER'],
  // Exams
  'exam.schedule': ['ADMIN', 'EXAM_CONTROLLER'],
  'exam.read': ['ADMIN', 'EXAM_CONTROLLER', 'FACULTY', 'STUDENT'],
  'exam.enterGrades': ['ADMIN', 'FACULTY'],
  'exam.publish': ['ADMIN', 'EXAM_CONTROLLER'],
  'exam.examGrades': ['ADMIN', 'EXAM_CONTROLLER', 'FACULTY'],
  'exam.studentGrades': ['ADMIN', 'EXAM_CONTROLLER', 'STUDENT'],
  'exam.compileResult': ['ADMIN', 'EXAM_CONTROLLER'],
  'exam.confirmRegistration': ['ADMIN', 'EXAM_CONTROLLER'],
  'exam.studentResults': ['ADMIN', 'EXAM_CONTROLLER', 'STUDENT'],
  // Attendance
  'att.mark': ['ADMIN', 'FACULTY'],
  'att.studentSummary': ['ADMIN', 'FACULTY', 'EXAM_CONTROLLER', 'STUDENT'],
  'att.courseShortage': ['ADMIN', 'FACULTY'],
  'att.markFaculty': ['ADMIN'],
  'att.facultyAttendance': ['ADMIN', 'FACULTY'],
  // Fees
  'fee.createInvoice': ['ADMIN', 'ACCOUNTS'],
  'fee.invoicesByStatus': ['ADMIN', 'ACCOUNTS'],
  'fee.recordPayment': ['ADMIN', 'ACCOUNTS'],
  'fee.studentInvoices': 'AUTH',
  'fee.invoicePayments': 'AUTH',
  // Hostel
  'hostel.createRoom': ['ADMIN', 'HOSTEL_ADMIN'],
  'hostel.allRooms': ['ADMIN', 'HOSTEL_ADMIN'],
  'hostel.availableRooms': ['ADMIN', 'HOSTEL_ADMIN', 'STUDENT'],
  'hostel.apply': ['STUDENT'],
  'hostel.pay': ['STUDENT'],
  'hostel.approve': ['ADMIN', 'HOSTEL_ADMIN'],
  'hostel.reject': ['ADMIN', 'HOSTEL_ADMIN'],
  'hostel.allot': ['ADMIN', 'HOSTEL_ADMIN'],
  'hostel.vacate': ['ADMIN', 'HOSTEL_ADMIN'],
  'hostel.studentAllotments': ['ADMIN', 'HOSTEL_ADMIN', 'STUDENT'],
  // Bookings
  'booking.book': 'AUTH',
  'booking.byUser': 'AUTH',
  'booking.all': ['ADMIN'],
  'booking.updateStatus': ['ADMIN'],
  // Notifications
  'notif.send': 'AUTH',
  'notif.inbox': 'AUTH',
  // Timetable
  'tt.create': ['ADMIN'],
  'tt.all': ['ADMIN', 'FACULTY'],
  'tt.byCourse': ['ADMIN', 'FACULTY', 'STUDENT'],
  'tt.studentSchedule': ['ADMIN', 'FACULTY', 'STUDENT'],
  // Academic standing (item 7)
  'standing.viewAll': ['ADMIN', 'FACULTY'],
  'standing.viewOwn': ['STUDENT'],
  // Logs (item 10)
  'logs.view': ['ADMIN'],
  // Holidays (item 8) — everyone except APPLICANT
  'holiday.view': ['STUDENT', 'FACULTY', 'EXAM_CONTROLLER', 'ACCOUNTS', 'ADMIN', 'HOSTEL_ADMIN'],
}

export function can(role, action) {
  const rule = PERMS[action]
  if (!rule) return false
  if (rule === 'AUTH') return !!role
  return !!role && rule.includes(role)
}

// ---- Navigation. Each item lists the roles that can see the module,
// kept consistent with the PERMS capability map above. ----
export const NAV = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: 'all' },
  // item 6: applicants apply; students are already enrolled, so no Admissions tab for them.
  { to: '/admissions', label: 'Admissions', icon: GraduationCap, roles: ['APPLICANT', 'ADMIN'] },
  { to: '/users', label: 'Users', icon: UserCog, roles: ['ADMIN', 'FACULTY', 'EXAM_CONTROLLER', 'ACCOUNTS'] },
  { to: '/programs', label: 'Programs', icon: Layers, roles: 'all' },
  { to: '/departments', label: 'Departments', icon: Building2, roles: 'all' },
  { to: '/courses', label: 'Courses', icon: BookOpen, roles: 'all' },
  { to: '/registrations', label: 'Registrations', icon: ClipboardList, roles: ['ADMIN', 'STUDENT', 'FACULTY', 'EXAM_CONTROLLER'] },
  { to: '/exams', label: 'Exams & Grades', icon: FileSpreadsheet, roles: ['ADMIN', 'EXAM_CONTROLLER', 'FACULTY', 'STUDENT'] },
  { to: '/attendance', label: 'Attendance', icon: CalendarCheck, roles: ['ADMIN', 'FACULTY', 'EXAM_CONTROLLER', 'STUDENT'] },
  { to: '/academic-standing', label: 'Academic Standing', icon: Award, roles: ['ADMIN', 'FACULTY', 'STUDENT'] },
  { to: '/fees', label: 'Fees', icon: Wallet, roles: ['ADMIN', 'ACCOUNTS', 'STUDENT'] },
  { to: '/hostel', label: 'Hostel', icon: BedDouble, roles: ['ADMIN', 'HOSTEL_ADMIN', 'STUDENT'] },
  { to: '/bookings', label: 'Facility Bookings', icon: CalendarClock, roles: 'all' },
  { to: '/timetable', label: 'Timetable', icon: CalendarDays, roles: ['ADMIN', 'FACULTY', 'STUDENT'] },
  // item 8: holidays visible to all except applicants
  { to: '/holidays', label: 'Holidays', icon: PartyPopper, roles: ['STUDENT', 'FACULTY', 'EXAM_CONTROLLER', 'ACCOUNTS', 'ADMIN', 'HOSTEL_ADMIN'] },
  // item 10: audit & module logs, admin only
  { to: '/logs', label: 'System Logs', icon: ScrollText, roles: ['ADMIN'] },
  { to: '/notifications', label: 'Notifications', icon: Bell, roles: 'all' },
]

export function navForRole(role) {
  return NAV.filter((n) => n.roles === 'all' || (role && n.roles.includes(role)))
}
