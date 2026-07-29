import { useState } from 'react'
import { CalendarCheck, Plus, Trash2, Search, Save } from 'lucide-react'
import { AttendanceApi } from '../lib/services'
import { asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { ATTENDANCE_STATUS, can } from '../lib/constants'
import { FacultySelect } from '../components/ui/FacultySelect'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Field, Input, Select,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'

export default function Attendance() {
  const toast = useToast()
  const { user } = useAuth()
  const role = user?.role
  const tabs = [
    ...(can(role, 'att.mark') ? [{ key: 'mark', label: 'Mark attendance' }] : []),
    ...(can(role, 'att.studentSummary') ? [{ key: 'summary', label: 'Student summary' }] : []),
    ...(can(role, 'att.courseShortage') ? [{ key: 'shortage', label: 'Course shortage' }] : []),
    ...(can(role, 'att.facultyAttendance') ? [{ key: 'faculty', label: 'Faculty attendance' }] : []),
  ]
  const [tab, setTab] = useState(tabs[0]?.key || 'summary')

  return (
    <div>
      <PageHeader icon={CalendarCheck} title="Attendance" subtitle="Record lectures, review summaries and track shortages." />
      {tabs.length > 1 && <Tabs active={tab} onChange={setTab} tabs={tabs} />}
      {tab === 'mark' && can(role, 'att.mark') && <MarkAttendance toast={toast} />}
      {tab === 'summary' && <StudentSummary toast={toast} defaultId={role === 'STUDENT' ? user?.userId : ''} />}
      {tab === 'shortage' && can(role, 'att.courseShortage') && <CourseShortage toast={toast} />}
      {tab === 'faculty' && can(role, 'att.facultyAttendance') && <FacultyAttendance toast={toast} canMark={can(role, 'att.markFaculty')} />}
    </div>
  )
}

function MarkAttendance({ toast }) {
  const [meta, setMeta] = useState({ courseId: '', lectureDate: '' })
  const [records, setRecords] = useState([{ studentId: '', status: 'PRESENT' }])
  const [saving, setSaving] = useState(false)
  const upd = (i, k, v) => setRecords((r) => r.map((x, idx) => (idx === i ? { ...x, [k]: v } : x)))

  const submit = async () => {
    setSaving(true)
    try {
      await AttendanceApi.mark({
        courseId: Number(meta.courseId), lectureDate: meta.lectureDate,
        records: records.filter((r) => r.studentId).map((r) => ({ studentId: Number(r.studentId), status: r.status })),
      })
      toast.success('Attendance recorded')
      setRecords([{ studentId: '', status: 'PRESENT' }])
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  return (
    <Card className="p-6">
      <div className="grid sm:grid-cols-2 gap-4 mb-5">
        <Field label="Course ID"><Input type="number" min={1} max={999999} value={meta.courseId} onChange={(e) => setMeta({ ...meta, courseId: e.target.value })} placeholder="1" /></Field>
        <Field label="Lecture date"><Input type="date" value={meta.lectureDate} onChange={(e) => setMeta({ ...meta, lectureDate: e.target.value })} /></Field>
      </div>
      <div className="space-y-2 mb-4">
        <span className="label">Student records</span>
        {records.length > 0 && (
          <div className="flex gap-2 px-1">
            <span className="text-xs font-semibold flex-1" style={{ color: 'var(--text-faint)' }}>Student ID</span>
            <span className="text-xs font-semibold w-40" style={{ color: 'var(--text-faint)' }}>Status</span>
            <span className="w-9 shrink-0" />
          </div>
        )}
        {records.map((r, i) => (
          <div key={i} className="flex gap-3 items-center">
            <input
              type="number"
              min={1}
              max={999999}
              className="field flex-2 min-w-1"
              value={r.studentId}
              onChange={(e) => upd(i, 'studentId', e.target.value)}
              
            />
            <select
              className="field w-35 shrink-30"
              value={r.status}
              onChange={(e) => upd(i, 'status', e.target.value)}
            >
              {ATTENDANCE_STATUS.map((s) => (
                <option key={s} value={s}>{String(s).replace(/_/g, ' ')}</option>
              ))}
            </select>
            <button onClick={() => setRecords((rs) => rs.filter((_, idx) => idx !== i))} className="p-2 rounded-lg hover:bg-rose-500/10 text-rose-500 shrink-0"><Trash2 size={16} /></button>
          </div>
        ))}
        <Button variant="subtle" size="sm" onClick={() => setRecords((r) => [...r, { studentId: '', status: 'PRESENT' }])}><Plus size={14} /> Add student</Button>
      </div>
      <Button onClick={submit} loading={saving}><Save size={16} /> Record attendance</Button>
    </Card>
  )
}

function StudentSummary({ toast, defaultId }) {
  const [q, setQ] = useState({ studentId: defaultId || '', academicYear: '' })
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const load = async () => {
    setLoading(true)
    try { setData(asArray(await AttendanceApi.studentSummary(Number(q.studentId), q.academicYear || undefined))) }
    catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }
  return (
    <>
      <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
        <div className="w-40"><span className="label">Student ID</span><Input type="number" min={1} max={999999} value={q.studentId} onChange={(e) => setQ({ ...q, studentId: e.target.value })} placeholder="5" /></div>
        <div className="w-40"><span className="label">Academic year</span><Input value={q.academicYear} onChange={(e) => setQ({ ...q, academicYear: e.target.value })} placeholder="2026-27" /></div>
        <Button onClick={load} loading={loading}><Search size={16} /> Load</Button>
      </Card>
      <Card className="p-4">
        {loading ? <Spinner /> : !data ? <EmptyState icon={CalendarCheck} title="Look up a student" hint="Enter a student ID to see per-course attendance." />
          : data.length === 0 ? <EmptyState icon={CalendarCheck} title="No records" />
          : <Table head={['Course', 'Attended', 'Total', 'Percentage', 'Standing']}>
              {data.map((s) => (
                <Row key={s.summaryId}>
                  <Cell>{s.courseName || s.courseId}</Cell>
                  <Cell>{s.attendedLectures ?? '—'}</Cell>
                  <Cell>{s.totalLectures ?? '—'}</Cell>
                  <Cell>{s.attendancePercent != null ? `${s.attendancePercent}%` : '—'}</Cell>
                  <Cell><Badge value={s.shortageFlag ? 'DETAINED' : 'GOOD'} /></Cell>
                </Row>
              ))}
            </Table>}
      </Card>
    </>
  )
}

function CourseShortage({ toast }) {
  const [courseId, setCourseId] = useState('')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const load = async () => {
    setLoading(true)
    try { setData(asArray(await AttendanceApi.courseShortage(Number(courseId)))) }
    catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }
  return (
    <>
      <Card className="p-4 mb-5 flex items-end gap-3">
        <div className="w-40"><span className="label">Course ID</span><Input type="number" min={1} max={999999} value={courseId} onChange={(e) => setCourseId(e.target.value)} placeholder="1" /></div>
        <Button onClick={load} loading={loading}><Search size={16} /> Load shortage list</Button>
      </Card>
      <Card className="p-4">
        {loading ? <Spinner /> : !data ? <EmptyState icon={CalendarCheck} title="Check shortages" hint="Enter a course ID to list students below the threshold." />
          : data.length === 0 ? <EmptyState icon={CalendarCheck} title="No shortages" hint="Everyone meets the attendance requirement." />
          : <Table head={['Student', 'Attended', 'Total', 'Percentage']}>
              {data.map((s) => (
                <Row key={s.summaryId}>
                  <Cell>{s.studentName || s.studentId}</Cell>
                  <Cell>{s.attendedLectures ?? '—'}</Cell>
                  <Cell>{s.totalLectures ?? '—'}</Cell>
                  <Cell><span className="text-rose-500 font-semibold">{s.attendancePercent != null ? `${s.attendancePercent}%` : '—'}</span></Cell>
                </Row>
              ))}
            </Table>}
      </Card>
    </>
  )
}

function FacultyAttendance({ toast, canMark }) {
  const [form, setForm] = useState({ facultyName: '', date: '', status: 'PRESENT' })
  const [saving, setSaving] = useState(false)
  const [facultyId, setFacultyId] = useState('')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)

  const mark = async () => {
    setSaving(true)
    try { await AttendanceApi.markFaculty(form); toast.success('Faculty attendance marked') }
    catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }
  const load = async () => {
    setLoading(true)
    try { setData(asArray(await AttendanceApi.facultyAttendance(Number(facultyId)))) }
    catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }

  return (
    <div className={`grid gap-6 ${canMark ? 'lg:grid-cols-2' : 'lg:grid-cols-1'}`}>
      {canMark && (
        <Card className="p-6">
          <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>Mark faculty attendance</h3>
          <div className="space-y-4">
            <Field label="Faculty name"><FacultySelect byName value={form.facultyName} onChange={(name) => setForm({ ...form, facultyName: name })} /></Field>
            <div className="grid grid-cols-2 gap-4">
              <Field label="Date"><Input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /></Field>
              <Field label="Status"><Select options={ATTENDANCE_STATUS} value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} /></Field>
            </div>
            <Button onClick={mark} loading={saving}><Save size={16} /> Mark</Button>
          </div>
        </Card>
      )}
      <Card className="p-6">
        <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>View faculty attendance</h3>
        <div className="flex items-end gap-3 mb-4">
          <div className="flex-1"><span className="label">Faculty</span><FacultySelect value={facultyId} onChange={(id) => setFacultyId(id ?? '')} /></div>
          <Button onClick={load} loading={loading}><Search size={16} /> Load</Button>
        </div>
        {loading ? <Spinner /> : !data ? <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Enter a faculty ID to view their attendance log.</p>
          : data.length === 0 ? <EmptyState icon={CalendarCheck} title="No records" />
          : <Table head={['Date', 'Status']}>
              {data.map((r) => (<Row key={r.id}><Cell>{r.date}</Cell><Cell><Badge value={r.status} /></Cell></Row>))}
            </Table>}
      </Card>
    </div>
  )
}
