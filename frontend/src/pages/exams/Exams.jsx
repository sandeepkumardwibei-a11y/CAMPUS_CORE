import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileSpreadsheet, Plus, Search, ArrowRight, Calculator, CheckCheck } from 'lucide-react'
import { ExamApi, CourseApi } from '../../lib/services'
import { useAsync, asArray } from '../../lib/hooks'
import { apiMessage } from '../../lib/api'
import { useToast } from '../../context/ToastContext'
import { EXAM_TYPES, can } from '../../lib/constants'
import { isHoliday, HOLIDAY_MAP } from '../../lib/holidays'
import { useAuth } from '../../context/AuthContext'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Modal, Field, Input, Select,
} from '../../components/ui'
import { Tabs } from '../../components/ui/extras'

const empty = { courseId: '', semester: 3, academicYear: '2026-27', examType: 'INTERNAL', examDate: '', startTime: '', durationMins: 90, venue: '', maxMarks: 100 }

// College hours + break windows (mirrors backend guards)
const DAY_START = '08:00', DAY_END = '16:00'
const BREAKS = [['10:00', '10:15'], ['12:00', '13:00'], ['14:45', '15:00']]
const toMin = (t) => { if (!t) return 0; const [h, m] = String(t).split(':').map(Number); return h * 60 + (m || 0) }
const overlapsBreak = (s, e) => BREAKS.some(([bs, be]) => toMin(s) < toMin(be) && toMin(e) > toMin(bs))
const withinHours = (s, e) => toMin(s) >= toMin(DAY_START) && toMin(e) <= toMin(DAY_END)

export default function Exams() {
  const toast = useToast()
  const [tab, setTab] = useState('exams')
  return (
    <div>
      <PageHeader icon={FileSpreadsheet} title="Exams & Grades" subtitle="Schedule exams, enter grades and compile results." />
      <Tabs active={tab} onChange={setTab} tabs={[
        { key: 'exams', label: 'Exams' },
        { key: 'student', label: 'Student results' },
      ]} />
      {tab === 'exams' && <ExamList toast={toast} />}
      {tab === 'student' && <StudentResults toast={toast} />}
    </div>
  )
}

function ExamList({ toast }) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const canSchedule = can(user?.role, 'exam.schedule')
  const { data: coursesData } = useAsync(() => (canSchedule ? CourseApi.all() : Promise.resolve([])), [canSchedule])
  const courses = asArray(coursesData)
  const [mode, setMode] = useState('list') // 'list' | 'course'
  const [q, setQ] = useState({ academicYear: '2026-27', semester: '3', courseId: '' })
  const [rows, setRows] = useState(null)
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const load = async () => {
    setLoading(true)
    try {
      if (mode === 'course') setRows(asArray(await ExamApi.byCourse(Number(q.courseId), q.academicYear || undefined)))
      else setRows(asArray(await ExamApi.list({ academicYear: q.academicYear || undefined, semester: q.semester || undefined, page: 0, size: 50 })))
    } catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }
  const schedule = async () => {
    // Client guards mirror the backend (items 8 & 11)
    if (isHoliday(form.examDate)) {
      toast.error(`${form.examDate} is a holiday (${HOLIDAY_MAP[form.examDate].name}). Exams can't be scheduled on holidays.`); return
    }
    const endTime = (() => {
      const s = toMin(form.startTime) + Number(form.durationMins || 0)
      return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
    })()
    if (!withinHours(form.startTime, endTime)) {
      toast.error('Exam must fall within college hours (08:00–16:00).'); return
    }
    if (overlapsBreak(form.startTime, endTime)) {
      toast.error('That time overlaps a break. Pick a slot outside 10:00–10:15, 12:00–13:00, or 14:45–15:00.'); return
    }
    setSaving(true)
    try {
      await ExamApi.schedule({
        courseId: Number(form.courseId), semester: Number(form.semester), academicYear: form.academicYear,
        examType: form.examType, examDate: form.examDate, startTime: form.startTime,
        durationMins: Number(form.durationMins), venue: form.venue, maxMarks: Number(form.maxMarks),
      })
      toast.success('Exam scheduled'); setOpen(false); setForm(empty); load()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  return (
    <>
      {canSchedule && (
        <div className="flex justify-end mb-4">
          <Button onClick={() => setOpen(true)}><Plus size={16} /> Schedule exam</Button>
        </div>
      )}
      <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
        <div className="w-40"><span className="label">Query by</span>
          <Select value={mode} onChange={(e) => { setMode(e.target.value); setRows(null) }}
            options={[{ value: 'list', label: 'Year + semester' }, { value: 'course', label: 'Course' }]} />
        </div>
        {mode === 'course'
          ? <div className="w-56"><span className="label">Course</span>
              <Select value={q.courseId} onChange={(e) => setQ({ ...q, courseId: e.target.value })} placeholder="Select a course"
                options={courses.map((c) => ({ value: c.courseId, label: `${c.courseName} (${c.courseCode})` }))} /></div>
          : <div className="w-28"><span className="label">Semester</span><Input type="number" min={1} max={8} value={q.semester} onChange={(e) => setQ({ ...q, semester: e.target.value })} placeholder="3" /></div>}
        <div className="w-36"><span className="label">Academic year</span><Input value={q.academicYear} onChange={(e) => setQ({ ...q, academicYear: e.target.value })} placeholder="2026-27" /></div>
        <Button onClick={load} loading={loading}><Search size={16} /> Load exams</Button>
      </Card>

      <Card className="p-4">
        {loading ? <Spinner /> : !rows ? <EmptyState icon={FileSpreadsheet} title="Load exams" hint="Query by year+semester or by course." />
          : rows.length === 0 ? <EmptyState icon={FileSpreadsheet} title="No exams found" />
          : <Table head={['ID', 'Course', 'Type', 'Date', 'Time', 'Venue', 'Max', 'Status', '']}>
              {rows.map((x) => (
                <Row key={x.examId}>
                  <Cell mono>{x.examId}</Cell>
                  <Cell>{x.courseName || x.courseId}</Cell>
                  <Cell><Badge value={x.examType} /></Cell>
                  <Cell>{x.examDate}</Cell>
                  <Cell>{x.startTime}</Cell>
                  <Cell>{x.venue}</Cell>
                  <Cell>{x.maxMarks}</Cell>
                  <Cell><Badge value={x.status} /></Cell>
                  <Cell><button onClick={() => navigate(`/exams/${x.examId}`)} title="Open" className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-400"><ArrowRight size={16} /></button></Cell>
                </Row>
              ))}
            </Table>}
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title="Schedule exam" size="lg">
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <Field label="Course">
              <Select value={form.courseId} onChange={set('courseId')} placeholder="Select a course"
                options={courses.map((c) => ({ value: c.courseId, label: `${c.courseName} (${c.courseCode})` }))} />
            </Field>
            <Field label="Semester"><Input type="number" min={1} max={8} value={form.semester} onChange={set('semester')} /></Field>
            <Field label="Academic year"><Input value={form.academicYear} onChange={set('academicYear')} /></Field>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Field label="Exam type"><Select options={EXAM_TYPES} value={form.examType} onChange={set('examType')} /></Field>
            <Field label="Date" hint={form.examDate && isHoliday(form.examDate) ? `⚠ Holiday: ${HOLIDAY_MAP[form.examDate].name}` : 'No holidays / breaks allowed'}>
              <Input type="date" value={form.examDate} onChange={set('examDate')} />
            </Field>
            <Field label="Start time"><Input type="time" value={form.startTime} onChange={set('startTime')} /></Field>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Field label="Duration (mins)"><Input type="number" value={form.durationMins} onChange={set('durationMins')} /></Field>
            <Field label="Venue"><Input value={form.venue} onChange={set('venue')} placeholder="Hall A" /></Field>
            <Field label="Max marks"><Input type="number" value={form.maxMarks} onChange={set('maxMarks')} /></Field>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={schedule} loading={saving}>Schedule</Button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function StudentResults({ toast }) {
  const [studentId, setStudentId] = useState('')
  const [grades, setGrades] = useState(null)
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)
  const [compile, setCompile] = useState({ academicYear: '2026-27', semester: '3' })
  const [regId, setRegId] = useState('')

  const load = async () => {
    if (!studentId) return toast.error('Enter a student ID')
    setLoading(true)
    try {
      const [g, r] = await Promise.all([
        ExamApi.studentGrades(Number(studentId)).catch(() => []),
        ExamApi.studentResults(Number(studentId)).catch(() => []),
      ])
      setGrades(asArray(g)); setResults(asArray(r))
    } catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }
  const doCompile = async () => {
    try {
      await ExamApi.compileResult(Number(studentId), { academicYear: compile.academicYear, semester: compile.semester })
      toast.success('Result compiled'); load()
    } catch (e) { toast.error(apiMessage(e)) }
  }
  const confirmReg = async () => {
    try { await ExamApi.confirmRegistration(Number(regId)); toast.success('Registration confirmed') }
    catch (e) { toast.error(apiMessage(e)) }
  }

  return (
    <>
      <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
        <div className="w-40"><span className="label">Student ID</span><Input type="number" min={1} max={999999} value={studentId} onChange={(e) => setStudentId(e.target.value)} placeholder="5" /></div>
        <Button onClick={load} loading={loading}><Search size={16} /> Load</Button>
        <div className="flex-1" />
        <div className="w-28"><span className="label">Year</span><Input value={compile.academicYear} onChange={(e) => setCompile({ ...compile, academicYear: e.target.value })} /></div>
        <div className="w-20"><span className="label">Sem</span><Input type="number" min={1} max={8} value={compile.semester} onChange={(e) => setCompile({ ...compile, semester: e.target.value })} /></div>
        <Button variant="subtle" onClick={doCompile} disabled={!studentId}><Calculator size={16} /> Compile result</Button>
      </Card>

      <div className="grid lg:grid-cols-2 gap-6 mb-6">
        <Card className="p-4">
          <h3 className="font-display font-semibold mb-3" style={{ color: 'var(--text)' }}>Grades</h3>
          {!grades ? <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Load a student to see grades.</p>
            : grades.length === 0 ? <EmptyState icon={FileSpreadsheet} title="No grades" />
            : <Table head={['Course', 'Marks', 'Grade', 'Status']}>
                {grades.map((g, i) => (
                  <Row key={i}>
                    <Cell>{g.courseName || g.courseId}</Cell>
                    <Cell>{g.marksObtained ?? g.marks ?? '—'}</Cell>
                    <Cell>{g.grade ?? '—'}</Cell>
                    <Cell><Badge value={g.status} /></Cell>
                  </Row>
                ))}
              </Table>}
        </Card>
        <Card className="p-4">
          <h3 className="font-display font-semibold mb-3" style={{ color: 'var(--text)' }}>Results</h3>
          {!results ? <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Load a student to see results.</p>
            : results.length === 0 ? <EmptyState icon={FileSpreadsheet} title="No results" />
            : <Table head={['Year', 'Sem', 'GPA', 'Status']}>
                {results.map((r, i) => (
                  <Row key={i}>
                    <Cell>{r.academicYear}</Cell>
                    <Cell>{r.semester}</Cell>
                    <Cell>{r.gpa ?? r.cgpa ?? '—'}</Cell>
                    <Cell><Badge value={r.status} /></Cell>
                  </Row>
                ))}
              </Table>}
        </Card>
      </div>

      <Card className="p-4 flex flex-wrap items-end gap-3">
        <div>
          <span className="label">Confirm a semester registration (exam eligibility)</span>
          <div className="flex items-end gap-3">
            <div className="w-40"><Input type="number" min={1} max={999999} value={regId} onChange={(e) => setRegId(e.target.value)} placeholder="Registration ID" /></div>
            <Button variant="subtle" onClick={confirmReg} disabled={!regId}><CheckCheck size={16} /> Confirm registration</Button>
          </div>
        </div>
      </Card>
    </>
  )
}
