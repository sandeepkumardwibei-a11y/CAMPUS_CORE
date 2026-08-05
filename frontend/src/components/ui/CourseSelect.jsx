import { useEffect, useState } from 'react'
import { CourseApi } from '../../lib/services'
import { activeOnly } from '../../lib/hooks'
import { useAuth } from '../../context/AuthContext'

// A dropdown of courses, ordered by course ID (ascending), showing only ACTIVE
// courses. Emits the selected courseId (number) or null.
//
// Props:
//   facultyScoped  when true AND the logged-in user is FACULTY, list only the
//                  courses that faculty teaches (CourseApi.byFaculty). Everyone
//                  else (ADMIN, EXAM_CONTROLLER, …) still sees all courses.
export function CourseSelect({ value, onChange, placeholder = 'Select course', className = '', facultyScoped = false }) {
  const { user } = useAuth()
  const role = user?.role
  const [courses, setCourses] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    setLoading(true)
    const request = (facultyScoped && role === 'FACULTY' && user?.userId)
      ? CourseApi.byFaculty(Number(user.userId))
      : CourseApi.all()
    request
      .then((d) => { if (alive) setCourses(activeOnly(d, 'courseId')) })
      .catch(() => { if (alive) setCourses([]) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [facultyScoped, role, user?.userId])

  return (
    <select
      className={`field ${className}`.trim()}
      value={value ?? ''}
      disabled={loading}
      onChange={(e) => {
        const v = e.target.value
        onChange(v ? Number(v) : null)
      }}
    >
      <option value="">
        {loading ? 'Loading courses…' : (courses.length ? placeholder : 'No courses available')}
      </option>
      {courses.map((c) => (
        <option key={c.courseId} value={c.courseId}>
          {c.courseName}{c.courseCode ? ` (${c.courseCode})` : ''} · ID: {c.courseId}
        </option>
      ))}
    </select>
  )
}