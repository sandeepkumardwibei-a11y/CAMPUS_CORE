import { useMemo, useState, Fragment } from 'react'
import { CalendarDays, Plus, Search, LayoutGrid, List } from 'lucide-react'
import { TimetableApi, CourseApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { DAYS, can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Modal, Field, Input, Select,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'

const empty = { courseId: '', dayOfWeek: 'MONDAY', startTime: '', endTime: '', venue: '', academicYear: '2026-27', semester: 3 }

// Fixed daily structure (item 11)
const DAY_START = '08:00'
const DAY_END = '16:00'
const BREAKS = [
  { start: '10:00', end: '10:15', label: 'Short break' },
  { start: '12:00', end: '13:00', label: 'Lunch break' },
  { start: '14:45', end: '15:00', label: 'Short break' },
]

// Colour palette cycled per course for the grid view
const PALETTE = [
  'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 ring-emerald-500/30',
  'bg-sky-500/15 text-sky-600 dark:text-sky-300 ring-sky-500/30',
  'bg-violet-500/15 text-violet-600 dark:text-violet-300 ring-violet-500/30',
  'bg-amber-500/15 text-amber-600 dark:text-amber-300 ring-amber-500/30',
  'bg-rose-500/15 text-rose-600 dark:text-rose-300 ring-rose-500/30',
  'bg-teal-500/15 text-teal-600 dark:text-teal-300 ring-teal-500/30',
  'bg-fuchsia-500/15 text-fuchsia-600 dark:text-fuchsia-300 ring-fuchsia-500/30',
]

function toMin(t) {
  if (!t) return 0
  const [h, m] = String(t).split(':').map(Number)
  return h * 60 + (m || 0)
}
function hhmm(t) { return String(t || '').slice(0, 5) }

function overlapsBreak(start, end) {
  const s = toMin(start), e = toMin(end)
  return BREAKS.some((b) => s < toMin(b.end) && e > toMin(b.start))
}
function withinHours(start, end) {
  return toMin(start) >= toMin(DAY_START) && toMin(end) <= toMin(DAY_END)
}

// Build the hour rows 08:00..16:00
const HOURS = Array.from({ length: 8 }, (_, i) => 8 + i) // 8..15 (each row = 1h block)

function GridView({ slots }) {
  const days = DAYS // MON..SAT
  const colorFor = useMemo(() => {
    const map = {}
    let idx = 0
    for (const s of slots) {
      const key = s.courseCode || s.courseName || s.courseId
      if (!(key in map)) { map[key] = PALETTE[idx % PALETTE.length]; idx++ }
    }
    return map
  }, [slots])

  const slotFor = (day, hour) => slots.filter((s) =>
    s.dayOfWeek === day &&
    toMin(hhmm(s.startTime)) < (hour + 1) * 60 &&
    toMin(hhmm(s.endTime)) > hour * 60)

  const breakAt = (hour) => BREAKS.find((b) => toMin(b.start) < (hour + 1) * 60 && toMin(b.end) > hour * 60)

  return (
    <div className="overflow-x-auto">
      <div className="min-w-[720px]">
        <div className="grid" style={{ gridTemplateColumns: `80px repeat(${days.length}, 1fr)` }}>
          <div className="p-2 text-xs font-semibold" style={{ color: 'var(--text-faint)' }}>Time</div>
          {days.map((d) => (
            <div key={d} className="p-2 text-center text-xs font-semibold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
              {d.slice(0, 3)}
            </div>
          ))}
          {HOURS.map((hour) => {
            const br = breakAt(hour)
            return (
              <Fragment key={`row-${hour}`}>
                <div className="p-2 text-[11px] font-mono border-t" style={{ color: 'var(--text-faint)', borderColor: 'var(--border)' }}>
                  {String(hour).padStart(2, '0')}:00
                </div>
                {days.map((d) => {
                  const cellSlots = slotFor(d, hour)
                  return (
                    <div key={`${d}-${hour}`} className="p-1 border-t min-h-[54px]" style={{ borderColor: 'var(--border)' }}>
                      {br && (
                        <div className="h-full w-full rounded-lg grid place-items-center text-[10px] font-semibold"
                          style={{ background: 'repeating-linear-gradient(45deg, rgba(120,120,140,0.08), rgba(120,120,140,0.08) 6px, transparent 6px, transparent 12px)', color: 'var(--text-faint)' }}>
                          {br.label}
                        </div>
                      )}
                      {!br && cellSlots.map((s) => (
                        <div key={s.timetableId} className={`rounded-lg px-2 py-1 mb-1 ring-1 ${colorFor[s.courseCode || s.courseName || s.courseId]}`}>
                          <p className="text-[11px] font-semibold leading-tight truncate">{s.courseName || s.courseCode || `Course ${s.courseId}`}</p>
                          <p className="text-[10px] opacity-80">{hhmm(s.startTime)}–{hhmm(s.endTime)}{s.venue ? ` · ${s.venue}` : ''}</p>
                        </div>
                      ))}
                    </div>
                  )
                })}
              </Fragment>
            )
          })}
        </div>

        {/* Legend of the fixed daily breaks */}
        <div className="flex flex-wrap gap-3 mt-4 text-[11px]" style={{ color: 'var(--text-muted)' }}>
          <span className="font-semibold">College hours 08:00–16:00 ·</span>
          {BREAKS.map((b) => (
            <span key={b.start}>🕒 {b.start}–{b.end} {b.label}</span>
          ))}
        </div>
      </div>
    </div>
  )
}

export default function Timetable() {
  const toast = useToast()
  const { user } = useAuth()
  const canManage = can(user?.role, 'tt.create')
  const canByCourse = can(user?.role, 'tt.byCourse')
  const [tab, setTab] = useState('all')
  const [view, setView] = useState('grid')
  const { data, loading, reload } = useAsync(() => TimetableApi.all(), [])
  const { data: coursesData } = useAsync(() => (canManage ? CourseApi.all() : Promise.resolve([])), [canManage])
  const courses = asArray(coursesData)
  const [scoped, setScoped] = useState(null)
  const [scopeLoading, setScopeLoading] = useState(false)
  const [q, setQ] = useState({ courseId: '', studentId: '', programId: '', academicYear: '2026-27', semester: '3' })
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const list = tab === 'all' ? asArray(data) : asArray(scoped)

  const runScope = async () => {
    setScopeLoading(true)
    try {
      if (tab === 'course') setScoped(await TimetableApi.byCourse(Number(q.courseId)))
      else setScoped(await TimetableApi.studentSchedule(Number(q.studentId), { programId: q.programId || undefined, academicYear: q.academicYear || undefined, semester: q.semester || undefined }))
    } catch (e) { toast.error(apiMessage(e)) } finally { setScopeLoading(false) }
  }

  const create = async () => {
    // Client-side guard mirrors the backend break/hours rules (item 11)
    if (!withinHours(form.startTime, form.endTime)) {
      toast.error('Classes must be within college hours (08:00–16:00).'); return
    }
    if (overlapsBreak(form.startTime, form.endTime)) {
      toast.error('That time overlaps a break. No classes can be scheduled during break times.'); return
    }
    setSaving(true)
    try {
      await TimetableApi.create({
        courseId: Number(form.courseId), dayOfWeek: form.dayOfWeek,
        startTime: form.startTime.length === 5 ? `${form.startTime}:00` : form.startTime,
        endTime: form.endTime.length === 5 ? `${form.endTime}:00` : form.endTime,
        venue: form.venue, academicYear: form.academicYear, semester: Number(form.semester),
      })
      toast.success('Timetable slot added'); setOpen(false); setForm(empty); reload()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  return (
    <div>
      <PageHeader icon={CalendarDays} title="Timetable" subtitle="Weekly class schedule — colour-coded and interactive."
        actions={
          <div className="flex gap-2">
            <div className="flex rounded-xl glass p-1">
              <button onClick={() => setView('grid')} title="Grid view"
                className={`px-2.5 py-1.5 rounded-lg ${view === 'grid' ? 'gradient-btn text-white' : ''}`}><LayoutGrid size={16} /></button>
              <button onClick={() => setView('list')} title="List view"
                className={`px-2.5 py-1.5 rounded-lg ${view === 'list' ? 'gradient-btn text-white' : ''}`}><List size={16} /></button>
            </div>
            {canManage && <Button onClick={() => setOpen(true)}><Plus size={16} /> New slot</Button>}
          </div>
        } />

      <Tabs active={tab} onChange={(t) => { setTab(t); setScoped(null) }} tabs={[
        { key: 'all', label: 'All slots' },
        ...(canByCourse ? [{ key: 'course', label: 'By course' }] : []),
        { key: 'student', label: 'Student schedule' },
      ]} />

      {tab !== 'all' && (
        <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
          {tab === 'course' ? (
            <div className="w-56"><span className="label">Course</span>
              <Select value={q.courseId} onChange={(e) => setQ({ ...q, courseId: e.target.value })} placeholder="Select a course"
                options={courses.map((c) => ({ value: c.courseId, label: `${c.courseName} (${c.courseCode})` }))} />
            </div>
          ) : (
            <>
              <div className="w-32"><span className="label">Student ID</span><Input type="number" value={q.studentId} onChange={(e) => setQ({ ...q, studentId: e.target.value })} placeholder="5" /></div>
              <div className="w-32"><span className="label">Program ID</span><Input type="number" value={q.programId} onChange={(e) => setQ({ ...q, programId: e.target.value })} placeholder="1" /></div>
              <div className="w-32"><span className="label">Year</span><Input value={q.academicYear} onChange={(e) => setQ({ ...q, academicYear: e.target.value })} placeholder="2026-27" /></div>
              <div className="w-24"><span className="label">Sem</span><Input type="number" value={q.semester} onChange={(e) => setQ({ ...q, semester: e.target.value })} placeholder="3" /></div>
            </>
          )}
          <Button onClick={runScope} loading={scopeLoading}><Search size={16} /> Search</Button>
        </Card>
      )}

      <Card className="p-4">
        {(loading || scopeLoading) ? <Spinner /> : list.length === 0 ? (
          <EmptyState icon={CalendarDays} title="No timetable slots" hint="Add a slot or adjust your search." />
        ) : view === 'grid' ? (
          <GridView slots={list} />
        ) : (
          <Table head={['ID', 'Day', 'Course', 'Start', 'End', 'Venue', 'Year', 'Sem']}>
            {list.map((t) => (
              <Row key={t.timetableId}>
                <Cell mono>{t.timetableId}</Cell>
                <Cell><Badge value={t.dayOfWeek} /></Cell>
                <Cell>{t.courseName || t.courseCode || t.courseId}</Cell>
                <Cell>{hhmm(t.startTime)}</Cell>
                <Cell>{hhmm(t.endTime)}</Cell>
                <Cell>{t.venue}</Cell>
                <Cell>{t.academicYear}</Cell>
                <Cell>{t.semester}</Cell>
              </Row>
            ))}
          </Table>
        )}
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title="Add timetable slot">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Course">
              <Select value={form.courseId} onChange={set('courseId')} placeholder="Select a course"
                options={courses.map((c) => ({ value: c.courseId, label: `${c.courseName} (${c.courseCode})` }))} />
            </Field>
            <Field label="Day"><Select options={DAYS} value={form.dayOfWeek} onChange={set('dayOfWeek')} /></Field>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Start time" hint="08:00–16:00, not during breaks"><Input type="time" value={form.startTime} onChange={set('startTime')} /></Field>
            <Field label="End time"><Input type="time" value={form.endTime} onChange={set('endTime')} /></Field>
          </div>
          <div className="rounded-xl p-3 text-[11px]" style={{ background: 'var(--surface-2, rgba(120,120,140,0.08))', color: 'var(--text-muted)' }}>
            Breaks (no classes): 10:00–10:15 · 12:00–13:00 (lunch) · 14:45–15:00
          </div>
          <Field label="Venue"><Input value={form.venue} onChange={set('venue')} placeholder="Room 101" /></Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Academic year"><Input value={form.academicYear} onChange={set('academicYear')} /></Field>
            <Field label="Semester"><Input type="number" value={form.semester} onChange={set('semester')} /></Field>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={create} loading={saving}>Add slot</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
