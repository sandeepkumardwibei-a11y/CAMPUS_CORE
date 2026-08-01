import api, { unwrap } from './api'

// Helper builders
const g = (url, params) => api.get(url, { params }).then(unwrap)
const p = (url, body, params) => api.post(url, body, { params }).then(unwrap)
const u = (url, body, params) => api.put(url, body, { params }).then(unwrap)

// ---------------- Auth (3) ----------------
export const AuthApi = {
  register: (body) => p('/auth/register', body),
  login: (body) => p('/auth/login', body),
  refresh: (refreshToken) => p('/auth/refresh', { refreshToken }),
}

// ---------------- Users (3) ----------------
export const UserApi = {
  me: () => g('/users/me'),
  list: (role) => g('/users', role ? { role } : undefined),
  updateStatus: (userId, status) => u(`/users/${userId}/status`, null, { status }),
}

// ---------------- Admissions (13) ----------------
export const AdmissionApi = {
  all: () => g('/admissions'),
  apply: (body) => p('/admissions/apply', body),
  evaluate: (id, approved) => p(`/admissions/${id}/evaluate`, null, { approved }),
  issueOffer: (id, feeDetailsRef) => p(`/admissions/${id}/issue-offer`, null, { feeDetailsRef }),
  acceptOffer: (id, params) => p(`/admissions/${id}/accept-offer`, null, params),
  rejectOffer: (id, reasonMessage) => p(`/admissions/${id}/reject-offer`, null, { reasonMessage }),
  withdraw: (id) => p(`/admissions/${id}/withdraw`),
  revokeOffer: (id, reasonMessage) => p(`/admissions/${id}/revoke-offer`, null, { reasonMessage }),
  verificationDetails: (id) => g(`/admissions/${id}/verification-details`),
  verifyDocuments: (id, isVerified) => p(`/admissions/${id}/verify-documents`, null, { isVerified }),
  issueAdmissionLetter: (id) => p(`/admissions/${id}/issue-admission-letter`),
  finalizeEnrollment: (id) => p(`/admissions/${id}/finalize-enrollment`),
  status: (id) => g(`/admissions/${id}/status`),
  offerDetails: (id) => g(`/admissions/${id}/offer-details`),
  basicInfo: (id) => g(`/admissions/${id}/basic-info`),
  uploadDocument: (id, docType, file) => {
    const fd = new FormData()
    fd.append('docType', docType)
    fd.append('file', file)
    return api.post(`/admissions/${id}/documents`, fd).then(unwrap)
  },
  documentSummary: (id) => g(`/admissions/${id}/documents`),
}

// ---------------- Programs (4) ----------------
export const ProgramApi = {
  create: (body) => p('/programs', body),
  all: () => g('/programs'),
  byId: (id) => g(`/programs/${id}`),
  updateStatus: (id, status) => u(`/programs/${id}/status`, null, { status }),
}

// ---------------- Departments (2) ----------------
export const DepartmentApi = {
  create: (body) => p('/departments', body),
  all: () => g('/departments'),
}

// ---------------- Courses (7) ----------------
export const CourseApi = {
  create: (body) => p('/courses', body),
  all: () => g('/courses'),
  byId: (id) => g(`/courses/${id}`),
  byProgram: (programId, semester) => g(`/courses/program/${programId}`, semester ? { semester } : undefined),
  byFaculty: (facultyId) => g(`/courses/faculty/${facultyId}`),
  assignFaculty: (id, facultyId) => u(`/courses/${id}/assign-faculty`, null, { facultyId }),
  updateStatus: (id, status) => u(`/courses/${id}/status`, null, { status }),
}

// ---------------- Semester Registrations (6) ----------------
export const RegistrationApi = {
  create: (body) => p('/registrations', body),
  byId: (id) => g(`/registrations/${id}`),
  byStudent: (studentId) => g(`/registrations/student/${studentId}`),
  all: () => g('/registrations'),
  byCourse: (courseId, params) => g(`/registrations/course/${courseId}`, params),
  confirm: (id) => u(`/registrations/${id}/confirm`),
}

// ---------------- Exams & Grades (11) ----------------
export const ExamApi = {
  schedule: (body) => p('/exams/schedule', body),
  byId: (id) => g(`/exams/${id}`),
  list: (params) => g('/exams', params),
  byCourse: (courseId, academicYear) => g(`/exams/course/${courseId}`, academicYear ? { academicYear } : undefined),
  enterGrades: (examId, facultyId, records) => p(`/exams/${examId}/grades`, records, { facultyId }),
  markConducted: (examId) => u(`/exams/${examId}/conduct`),
  publish: (examId) => u(`/exams/${examId}/publish`),
  examGrades: (examId) => g(`/exams/${examId}/grades`),
  studentGrades: (studentId) => g(`/exams/student/${studentId}/grades`),
  compileResult: (studentId, params) => p(`/exams/student/${studentId}/compile-result`, null, params),
  confirmRegistration: (regId) => u(`/exams/registrations/${regId}/confirm`),
  studentResults: (studentId) => g(`/exams/student/${studentId}/results`),
}

// ---------------- Attendance (5) ----------------
export const AttendanceApi = {
  mark: (body) => p('/attendance/mark', body),
  studentSummary: (studentId, academicYear) => g(`/attendance/student/${studentId}`, academicYear ? { academicYear } : undefined),
  courseShortage: (courseId) => g(`/attendance/course/${courseId}/shortage`),
  markFaculty: (body) => p('/attendance/faculty/mark', body),
  facultyAttendance: (facultyId) => g(`/attendance/faculty/${facultyId}`),
}

// ---------------- Fees (5) ----------------
export const FeeApi = {
  createInvoice: (body) => p('/fees/invoices', body),
  recordPayment: (body) => p('/fees/payments', body),
  studentInvoices: (studentId) => g(`/fees/student/${studentId}/invoices`),
  invoicePayments: (invoiceId) => g(`/fees/invoices/${invoiceId}/payments`),
  invoicesByStatus: (params) => g('/fees/invoices', params),
  submitProof: (formData) => api.post('/fees/payments/proof', formData).then(unwrap),
  pendingProofPayments: () => g('/fees/payments/pending'),
  confirmProofPayment: (paymentId) => u(`/fees/payments/${paymentId}/confirm`, null),
  rejectProofPayment: (paymentId, reason) => u(`/fees/payments/${paymentId}/reject`, { reason }),
  proofUrl: (paymentId) => `${api.defaults.baseURL}/fees/payments/${paymentId}/proof`,
}

// ---------------- Hostel (10) ----------------
export const HostelApi = {
  createRoom: (body) => p('/hostel/rooms', body),
  apply: (studentId, body) => p(`/hostel/applications/${studentId}`, body),
  approve: (appId) => u(`/hostel/applications/${appId}/approve`),
  reject: (appId, reason) => u(`/hostel/applications/${appId}/reject`, { reason }),
  pay: (appId, body) => u(`/hostel/applications/${appId}/pay`, body),
  rooms: () => g('/hostel/rooms'),
  availableRooms: () => g('/hostel/rooms/available'),
  allot: (body) => p('/hostel/allotments', body),
  vacate: (allotmentId) => u(`/hostel/allotments/${allotmentId}/vacate`),
  studentAllotments: (studentId) => g(`/hostel/student/${studentId}/allotments`),
  allApplications: () => g('/hostel/applications'),
  allAllotments: () => g('/hostel/allotments'),
}

// ---------------- Facility Bookings (4) ----------------
export const BookingApi = {
  book: (userId, body) => p('/bookings', body, { userId }),
  byUser: (userId) => g(`/bookings/user/${userId}`),
  all: () => g('/bookings'),
  updateStatus: (id, status) => u(`/bookings/${id}/status`, null, { status }),
}

// ---------------- Notifications (5) ----------------
export const NotificationApi = {
  send: (params) => p('/notifications/send', null, params),
  byUser: (userId, params) => g(`/notifications/user/${userId}`, params),
  unreadCount: (userId) => g(`/notifications/user/${userId}/unread-count`),
  markRead: (id) => u(`/notifications/${id}/read`),
  markAllRead: (userId) => u(`/notifications/user/${userId}/read-all`),
}

// ---------------- Timetable (4) ----------------
export const TimetableApi = {
  create: (body) => p('/timetable', body),
  all: () => g('/timetable'),
  byCourse: (courseId) => g(`/timetable/course/${courseId}`),
  studentSchedule: (studentId, params) => g(`/timetable/student/${studentId}`, params),
  mySchedule: () => g('/timetable/my-schedule'),
  myTeaching: () => g('/timetable/my-teaching'),
}

// ---------------- Academic Standing (item 7) ----------------
export const AcademicStandingApi = {
  all: () => g('/academic-standing'),
  forStudent: (studentId) => g(`/academic-standing/student/${studentId}`),
}

// ---------------- Logs (item 10, admin only) ----------------
export const LogApi = {
  audit: () => g('/logs/audit'),
  module: () => g('/logs/module'),
}
