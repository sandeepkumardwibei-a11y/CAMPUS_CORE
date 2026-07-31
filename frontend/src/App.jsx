import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/Layout'

import Landing from './pages/Landing'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Users from './pages/Users'
import Admissions from './pages/admissions/Admissions'
import Applications from './pages/admissions/Applications'
import AdmissionDetail from './pages/admissions/AdmissionDetail'
import Programs from './pages/Programs'
import Departments from './pages/Departments'
import Courses from './pages/Courses'
import Registrations from './pages/Registrations'
import Exams from './pages/exams/Exams'
import ExamDetail from './pages/exams/ExamDetail'
import Attendance from './pages/Attendance'
import Fees from './pages/Fees'
import Hostel from './pages/Hostel'
import Bookings from './pages/Bookings'
import Timetable from './pages/Timetable'
import Notifications from './pages/Notifications'
import AcademicStanding from './pages/AcademicStanding'
import Holidays from './pages/Holidays'
import Logs from './pages/Logs'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/welcome" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="users" element={<Users />} />
        <Route path="admissions" element={<Admissions />} />
        <Route path="applications" element={<Applications />} />
        <Route path="admissions/:id" element={<AdmissionDetail />} />
        <Route path="programs" element={<Programs />} />
        <Route path="departments" element={<Departments />} />
        <Route path="courses" element={<Courses />} />
        <Route path="registrations" element={<Registrations />} />
        <Route path="exams" element={<Exams />} />
        <Route path="exams/:id" element={<ExamDetail />} />
        <Route path="attendance" element={<Attendance />} />
        <Route path="fees" element={<Fees />} />
        <Route path="hostel" element={<Hostel />} />
        <Route path="bookings" element={<Bookings />} />
        <Route path="timetable" element={<Timetable />} />
        <Route path="academic-standing" element={<AcademicStanding />} />
        <Route path="holidays" element={<Holidays />} />
        <Route path="logs" element={<Logs />} />
        <Route path="notifications" element={<Notifications />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
