import { useEffect, useState } from 'react'
import { UserApi } from '../../lib/services'
import { asArray } from '../../lib/hooks'

// A dropdown of all registered FACULTY users. Emits the selected faculty's userId,
// or their name when `byName` is set (useful where the API expects a faculty name).
export function FacultySelect({ value, onChange, placeholder = 'Select faculty', className = '', includeUnassign = false, byName = false }) {
  const [faculty, setFaculty] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    UserApi.list('FACULTY')
      .then((d) => { if (alive) setFaculty(asArray(d)) })
      .catch(() => { if (alive) setFaculty([]) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [])

  return (
    <select
      className={`field ${className}`.trim()}
      value={value ?? ''}
      disabled={loading}
      onChange={(e) => {
        const v = e.target.value
        if (!v) return onChange(byName ? '' : null)
        onChange(byName ? v : Number(v))
      }}
    >
      <option value="">{loading ? 'Loading faculty…' : placeholder}</option>
      {includeUnassign && <option value="">— Unassigned —</option>}
      {faculty.map((f) => (
        <option key={f.userId} value={byName ? f.name : f.userId}>
          {f.name} (ID: {f.userId})
        </option>
      ))}
    </select>
  )
}
