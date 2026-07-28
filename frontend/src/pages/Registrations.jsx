import { useState } from 'react'
import { ClipboardList, Plus, Search, CheckCheck } from 'lucide-react'
import { RegistrationApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { can } from '../lib/constants'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState, Field, Input,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'

const empty = { programId: '', academicYear: '2026-27', semester: 1 }

// Show the actual course IDs assigned to a registration (e.g. "2, 3"),
// instead of a bare count, so it's clear which courses were auto-assigned.
function courseLabel(r) {
  if (Array.isArray(r.courses) && r.courses.length) return r.courses.map((c) => c.courseId).join(', ')
  if (Array.isArray(r.courseIds) && r.courseIds.length) return r.courseIds.join(', ')
  return '—'
}

export default function Registrations() {
  const toast = useToast()
  const { user } = useAuth()

  const isStudent = user?.role === 'STUDENT'
  const isFaculty = user?.role === 'FACULTY'

  // Restrict Faculty from rendering this view entirely
  if (isFaculty) {
    return null
  }

  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const create = async (onDone) => {
    setSaving(true)
    try {
      await RegistrationApi.create({
        studentId: Number(user?.userId),
        programId: Number(form.programId),
        academicYear: form.academicYear,
        semester: Number(form.semester),
        courseIds: [], // empty on purpose: backend auto-assigns every course for this program + semester
      })
      toast.success('Registration submitted')
      setForm(empty)
      if (onDone) onDone()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  // ---------------- STUDENT VIEW: only a "New registration" card ----------------
  if (isStudent) {
    return (
      <div>
        <PageHeader icon={ClipboardList} title="Semester Registration" subtitle="Register yourself for a semester." />
        <Card className="p-6 max-w-xl">
          <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>New registration</h3>
          <div className="space-y-4">
            <Field label="Program ID"><Input type="number" value={form.programId} onChange={set('programId')} placeholder="1" /></Field>
            <div className="grid grid-cols-2 gap-4">
              <Field label="Academic year"><Input value={form.academicYear} onChange={set('academicYear')} placeholder="2026-27" /></Field>
              <Field label="Semester"><Input type="number" value={form.semester} onChange={set('semester')} /></Field>
            </div>
            <p className="text-sm rounded-xl p-3" style={{ background: 'var(--surface-2, rgba(120,120,140,0.08))', color: 'var(--text-muted)' }}>
              Courses are assigned automatically. Every course allocated to your chosen program and semester
              will be added to your registration — you don't enter course IDs.
            </p>
            <Button onClick={() => create()} loading={saving}><Plus size={16} /> Register</Button>
          </div>
        </Card>
      </div>
    )
  }

  // ---------------- STAFF VIEW: browse / search / confirm (read-only) ----------------
  return <StaffView />
}

function StaffView() {
  const toast = useToast()
  const { user } = useAuth()
  
  const canConfirm = can(user?.role, 'reg.confirm')
  const canViewAll = can(user?.role, 'reg.all')
  const [tab, setTab] = useState(canViewAll ? 'all' : 'student')

  // Only hit GET /registrations (ADMIN/EXAM_CONTROLLER only) when allowed
  const { data, loading, reload } = useAsync(() => (canViewAll ? RegistrationApi.all() : Promise.resolve([])), [canViewAll])

  const [scoped, setScoped] = useState(null)
  const [searched, setSearched] = useState(false)
  const [scopeLoading, setScopeLoading] = useState(false)
  const [q, setQ] = useState({ studentId: '', courseId: '', regId: '' })

  const list = tab === 'all' ? asArray(data) : asArray(scoped)

  const runScope = async () => {
    setScopeLoading(true); setSearched(true)
    try {
      let res = []
      if (tab === 'student') res = await RegistrationApi.byStudent(Number(q.studentId))
      else if (tab === 'course') res = await RegistrationApi.byCourse(Number(q.courseId))
      else if (tab === 'id') res = await RegistrationApi.byId(Number(q.regId))
      setScoped(asArray(res))
    } catch (e) {
      // No match (or lookup failure): clear stale results
      setScoped([])
      const status = e?.response?.status
      if (status && status !== 404) toast.error(apiMessage(e))
    } finally { setScopeLoading(false) }
  }

  const confirm = async (id) => {
    try { await RegistrationApi.confirm(id); toast.success('Registration confirmed'); reload() }
    catch (e) { toast.error(apiMessage(e)) }
  }

  const emptyProps = tab === 'all'
    ? { title: 'No registrations', hint: 'No semester registrations exist yet.' }
    : searched
      ? { title: 'No results found', hint: 'No registrations match your search.' }
      : { title: 'Search registrations', hint: 'Enter a value above and press Search.' }

  return (
    <div>
      <PageHeader icon={ClipboardList} title="Semester Registrations" subtitle="Browse and confirm student registrations." />

      <Tabs active={tab} onChange={(t) => { setTab(t); setScoped(null); setSearched(false) }} tabs={[
        ...(canViewAll ? [{ key: 'all', label: 'All' }] : []), 
        { key: 'student', label: 'By student' },
        { key: 'course', label: 'By course' }, 
        { key: 'id', label: 'By ID' },
      ]} />

      {tab !== 'all' && (
        <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
          {tab === 'student' && <div className="w-40"><span className="label">Student ID</span><Input type="number" value={q.studentId} onChange={(e) => setQ({ ...q, studentId: e.target.value })} placeholder="5" /></div>}
          {tab === 'id' && <div className="w-40"><span className="label">Registration ID</span><Input type="number" value={q.regId} onChange={(e) => setQ({ ...q, regId: e.target.value })} placeholder="1" /></div>}
          {tab === 'course' && <div className="w-40"><span className="label">Course ID</span><Input type="number" value={q.courseId} onChange={(e) => setQ({ ...q, courseId: e.target.value })} placeholder="1" /></div>}
          <Button onClick={runScope} loading={scopeLoading}><Search size={16} /> Search</Button>
        </Card>
      )}

      <Card className="p-4">
        {(loading || scopeLoading) ? <Spinner /> : list.length === 0 ? (
          <EmptyState icon={ClipboardList} {...emptyProps} />
        ) : (
          <Table head={['ID', 'Student', 'Program', 'Year', 'Sem', 'Courses', 'Status', 'Action']}>
            {list.map((r) => (
              <Row key={r.registrationId}>
                <Cell mono>{r.registrationId}</Cell>
                <Cell>{r.studentName || r.studentId}</Cell>
                <Cell>{r.programName || r.programId}</Cell>
                <Cell>{r.academicYear}</Cell>
                <Cell>{r.semester}</Cell>
                <Cell mono>{courseLabel(r)}</Cell>
                <Cell><Badge value={r.status} /></Cell>
                <Cell>
                  {canConfirm && String(r.status).toUpperCase() !== 'CONFIRMED' && (
                    <Button size="sm" variant="subtle" onClick={() => confirm(r.registrationId)}><CheckCheck size={14} /> Confirm</Button>
                  )}
                </Cell>
              </Row>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}