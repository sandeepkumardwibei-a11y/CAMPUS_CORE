import { useEffect, useState } from 'react'
import { UserApi, RegistrationApi } from '../../lib/services'
import { asArray, activeOnly } from '../../lib/hooks'
import { useAuth } from '../../context/AuthContext'

// A dropdown of students showing "Name (ID: n)" and emitting the selected userId.
//
// Scoping rules:
//  • ADMIN and EXAM_CONTROLLER normally see ALL students.
//  • Everyone else (e.g. FACULTY) sees only students REGISTERED in `courseId`.
//  • `courseScoped` forces the "registered in this course" list for EVERY role
//    (used on Mark attendance, where the roster should always match the course).
//    If no courseId is provided yet, the list stays empty and prompts for a course.
//  • `allStudents` forces the full roster (used on summary/shortage lookups).
//
// Props:
//   value        selected userId (number|string) or '' for none
//   onChange     (userId:number|null) => void
//   courseId     restrict to students registered in this course
//   courseScoped always restrict to `courseId` registrants, regardless of role
//   allStudents  always show the full roster, regardless of role
export function StudentSelect({ value, onChange, courseId, placeholder = 'Select student', className = '', allStudents = false, courseScoped = false }) {
  const { user } = useAuth()
  const role = user?.role
  // `courseScoped` wins: it pins the list to the selected course for all roles.
  // Otherwise `allStudents` (or a privileged role) shows the full roster.
  const seesAll = !courseScoped && (allStudents || role === 'ADMIN' || role === 'EXAM_CONTROLLER')

  const [students, setStudents] = useState([])
  const [loading, setLoading] = useState(false)
  const [note, setNote] = useState('')

  useEffect(() => {
    let alive = true
    setNote('')

    // Privileged roles: load the full student roster once.
    if (seesAll) {
      setLoading(true)
      UserApi.list('STUDENT')
        .then((d) => { if (alive) setStudents(activeOnly(d, 'userId').map((u) => ({ id: u.userId, name: u.name }))) })
        .catch(() => { if (alive) setStudents([]) })
        .finally(() => { if (alive) setLoading(false) })
      return () => { alive = false }
    }

    // Non-privileged (faculty): must have a course, then load its registrants.
    if (!courseId) {
      setStudents([])
      setNote('Select a course first to load its registered students.')
      return () => { alive = false }
    }
    setLoading(true)
    RegistrationApi.byCourse(Number(courseId))
      .then((d) => {
        if (!alive) return
        // registrations carry studentId + studentName; de-dupe by student.
        const seen = new Set()
        const list = []
        asArray(d).forEach((r) => {
          if (r.studentId != null && !seen.has(r.studentId)) {
            seen.add(r.studentId)
            list.push({ id: r.studentId, name: r.studentName || `Student ${r.studentId}` })
          }
        })
        list.sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
        setStudents(list)
        if (!list.length) setNote('No students are registered in this course yet.')
      })
      .catch(() => { if (alive) { setStudents([]); setNote('Could not load registered students for this course.') } })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [seesAll, courseId])

  return (
    <div>
      <select
        className={`field ${className}`.trim()}
        value={value ?? ''}
        disabled={loading || (!seesAll && !courseId)}
        onChange={(e) => {
          const v = e.target.value
          onChange(v ? Number(v) : null)
        }}
      >
        <option value="">{loading ? 'Loading students…' : placeholder}</option>
        {students.map((s) => (
          <option key={s.id} value={s.id}>{s.name} (ID: {s.id})</option>
        ))}
      </select>
      {note && <p className="text-xs mt-1" style={{ color: 'var(--text-faint)' }}>{note}</p>}
    </div>
  )
}
