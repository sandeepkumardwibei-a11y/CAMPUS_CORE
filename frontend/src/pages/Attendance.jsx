import { useState } from 'react'
import { CalendarCheck, Plus, Trash2, Search, Save } from 'lucide-react'
import { AttendanceApi } from '../lib/services'
import { asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { ATTENDANCE_STATUS, can } from '../lib/constants'
import { FacultySelect } from '../components/ui/FacultySelect'
import { StudentSelect } from '../components/ui/StudentSelect'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Field, Input, Select,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'
import { PieChart, BarChart } from '../components/ui/charts'

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
        <Field label="Lecture date"><Input type="date" max={new Date().toISOString().slice(0, 10)} value={meta.lectureDate} onChange={(e) => setMeta({ ...meta, lectureDate: e.target.value })} /></Field>
      </div>
      <div className="space-y-2 mb-4">
        <span className="label">Student records</span>
        {records.length > 0 && (
          <div className="flex gap-3 px-1">
            <span className="text-xs font-semibold flex-1" style={{ color: 'var(--text-faint)' }}>Student</span>
            <span className="text-xs font-semibold w-36 shrink-0" style={{ color: 'var(--text-faint)' }}>Status</span>
            <span className="w-9 shrink-0" />
          </div>
        )}
        {records.map((r, i) => (
          <div key={i} className="flex gap-3 items-center">
            <div className="flex-1 min-w-0">
              <StudentSelect
                courseId={meta.courseId}
                value={r.studentId}
                onChange={(id) => upd(i, 'studentId', id ?? '')}
              />
            </div>
            <select
              className="field w-36 shrink-0"
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
  // Aggregate the per-course summaries into totals for the charts.
  const totals = (data || []).reduce(
    (acc, s) => {
      acc.present += Number(s.presentCount) || 0
      acc.late += Number(s.lateCount) || 0
      acc.absent += Number(s.absentCount) || 0
      acc.officialDuty += Number(s.officialDutyCount) || 0
      acc.total += Number(s.totalLectures) || 0
      return acc
    },
    { present: 0, late: 0, absent: 0, officialDuty: 0, total: 0 }
  )
  const pieData = [
    { label: 'Present', value: totals.present, color: '#10b981' },
    { label: 'Late', value: totals.late, color: '#f59e0b' },
    { label: 'Official duty', value: totals.officialDuty, color: '#6366f1' },
    { label: 'Absent', value: totals.absent, color: '#f43f5e' },
  ]
  // Did OD condonation kick in on any course? (shown as a small note)
  const odApplied = (data || []).some((s) => s.officialDutyApplied)
  // One bar per course = attendance percentage for that course.
  const barData = (data || []).map((s) => ({
    label: s.courseName || `Course ${s.courseId}`,
    value: s.attendancePercent != null ? Number(s.attendancePercent) : (s.totalLectures ? Math.round((s.attendedLectures / s.totalLectures) * 100) : 0),
  }))

  return (
    <>
      <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
        <div className="w-40"><span className="label">Student ID</span><Input type="number" min={1} max={999999} value={q.studentId} onChange={(e) => setQ({ ...q, studentId: e.target.value })} placeholder="5" /></div>
        <div className="w-40"><span className="label">Academic year</span><Input value={q.academicYear} onChange={(e) => setQ({ ...q, academicYear: e.target.value })} placeholder="2026-27" /></div>
        <Button onClick={load} loading={loading}><Search size={16} /> Load</Button>
      </Card>

      {data && data.length > 0 && (
        <div className="grid lg:grid-cols-2 gap-6 mb-5">
          <Card className="p-6">
            <PieChart title="Attendance breakdown (lectures)" data={pieData} />
            {odApplied && (
              <p className="text-xs mt-3 text-center" style={{ color: 'var(--text-muted)' }}>
                Official-duty days were added to offset a shortage in one or more courses.
              </p>
            )}
          </Card>
          <Card className="p-6">
            <BarChart title="Attendance % by course" data={barData} unit="%" max={100} />
          </Card>
        </div>
      )}

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
              <Field label="Date"><Input type="date" max={new Date().toISOString().slice(0, 10)} value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /></Field>
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

        {data && data.length > 0 && (() => {
          // Pie: full status breakdown for faculty (Present / Late / Official duty / Absent).
          const present = data.filter((r) => r.status === 'PRESENT').length
          const late = data.filter((r) => r.status === 'LATE').length
          const officialDuty = data.filter((r) => r.status === 'OFFICIAL_DUTY').length
          const absent = data.filter((r) => r.status === 'ABSENT').length
          const pie = [
            { label: 'Present', value: present, color: '#10b981' },
            { label: 'Late', value: late, color: '#f59e0b' },
            { label: 'Official duty', value: officialDuty, color: '#6366f1' },
            { label: 'Absent', value: absent, color: '#f43f5e' },
          ]
          // Bar: attendance % per month. Present, Late and Official duty all count as "attended".
          const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
          const ATTENDED = new Set(['PRESENT', 'LATE', 'OFFICIAL_DUTY'])
          const byMonth = {}
          data.forEach((r) => {
            if (!r.date) return
            const [y, m] = String(r.date).split('-')
            const key = `${y}-${m}`
            byMonth[key] = byMonth[key] || { total: 0, attended: 0, label: `${MONTHS[Number(m) - 1] || m} ${String(y).slice(2)}` }
            byMonth[key].total += 1
            if (ATTENDED.has(r.status)) byMonth[key].attended += 1
          })
          const bar = Object.keys(byMonth).sort().map((k) => ({
            label: byMonth[k].label,
            value: byMonth[k].total ? Math.round((byMonth[k].attended / byMonth[k].total) * 100) : 0,
          }))
          return (
            <div className="grid md:grid-cols-2 gap-6 mb-6">
              <PieChart title="Attendance breakdown" data={pie} size={160} />
              <BarChart title="Attendance % by month" data={bar} unit="%" max={100} />
            </div>
          )
        })()}

        {loading ? <Spinner /> : !data ? <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Enter a faculty ID to view their attendance log.</p>
          : data.length === 0 ? <EmptyState icon={CalendarCheck} title="No records" />
          : <Table head={['Date', 'Status']}>
              {data.map((r) => (<Row key={r.id}><Cell>{r.date}</Cell><Cell><Badge value={r.status} /></Cell></Row>))}
            </Table>}
      </Card>
    </div>
  )
}
