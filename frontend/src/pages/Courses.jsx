import { useState } from 'react'
import { BookOpen, Plus, Search, UserPlus, ChevronDown, X } from 'lucide-react'
import { CourseApi, ProgramApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { COURSE_STATUS, can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Modal, Field, Input, Select,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'
import { FacultySelect } from '../components/ui/FacultySelect'

const empty = { courseName: '', courseCode: '', credits: 4, programIds: [], semester: 1, facultyId: '', maxEnrollment: 60 }

// Multi-select of registered programs (item 4: many-to-many program<->course).
function ProgramMultiSelect({ programs, selected, onChange }) {
  const [open, setOpen] = useState(false)
  const toggle = (id) => onChange(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id])
  const labelFor = (id) => {
    const p = programs.find((x) => x.programId === id)
    return p ? `${p.programName}` : `Program ${id}`
  }
  return (
    <div className="relative">
      <button type="button" onClick={() => setOpen((o) => !o)}
        className="field w-full text-left flex items-center justify-between">
        <span className={selected.length ? '' : 'opacity-60'}>
          {selected.length ? `${selected.length} program${selected.length > 1 ? 's' : ''} selected` : 'Select programs'}
        </span>
        <ChevronDown size={16} />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-xl border shadow-lg p-1"
            style={{ background: 'var(--surface, #fff)', borderColor: 'var(--border, rgba(0,0,0,0.12))' }}>
            {programs.length === 0 ? (
              <div className="px-3 py-2 text-sm opacity-60">No programs available. Create programs first.</div>
            ) : programs.map((p) => (
              <label key={p.programId} className="flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer hover:bg-black/5">
                <input type="checkbox" checked={selected.includes(p.programId)} onChange={() => toggle(p.programId)} />
                <span className="text-sm">{p.programName} <span className="opacity-60">(ID: {p.programId})</span></span>
              </label>
            ))}
          </div>
        </>
      )}
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2">
          {selected.map((id) => (
            <span key={id} className="text-xs px-2 py-1 rounded-full inline-flex items-center gap-1"
              style={{ background: 'var(--surface-2, rgba(120,120,140,0.12))' }}>
              {labelFor(id)}
              <button type="button" onClick={() => toggle(id)} className="opacity-60 hover:opacity-100"><X size={12} /></button>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

export default function Courses() {
  const toast = useToast()
  const { user } = useAuth()
  const canManage = can(user?.role, 'course.create')
  const [tab, setTab] = useState('all')
  const { data, loading, reload } = useAsync(() => CourseApi.all(), [])
  const { data: programsData } = useAsync(() => ProgramApi.all(), [])
  const programs = asArray(programsData)
  const [scoped, setScoped] = useState(null)
  const [scopeLoading, setScopeLoading] = useState(false)
  const [q, setQ] = useState({ programId: '', semester: '', facultyId: '' })
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const [assign, setAssign] = useState(null) // {id, facultyId}
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const list = tab === 'all' ? asArray(data) : asArray(scoped)

  const runScope = async () => {
    setScopeLoading(true)
    try {
      if (tab === 'program') setScoped(await CourseApi.byProgram(Number(q.programId), q.semester || undefined))
      else setScoped(await CourseApi.byFaculty(Number(q.facultyId)))
    } catch (e) { toast.error(apiMessage(e)) } finally { setScopeLoading(false) }
  }

  const create = async () => {
    if (!form.programIds.length) { toast.error('Select at least one program'); return }
    setSaving(true)
    try {
      await CourseApi.create({
        courseName: form.courseName, courseCode: form.courseCode, credits: Number(form.credits),
        programIds: form.programIds, semester: Number(form.semester),
        facultyId: form.facultyId ? Number(form.facultyId) : null, maxEnrollment: Number(form.maxEnrollment),
      })
      toast.success('Course created'); setOpen(false); setForm(empty); reload()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  const doAssign = async () => {
    if (!assign?.facultyId) { toast.error('Select a faculty member'); return }
    try {
      await CourseApi.assignFaculty(assign.id, Number(assign.facultyId))
      toast.success('Faculty assigned'); setAssign(null); reload()
    } catch (e) { toast.error(apiMessage(e)) }
  }

  const updateStatus = async (id, status) => {
    try { await CourseApi.updateStatus(id, status); toast.success('Status updated'); reload(); window.dispatchEvent(new Event('cc:data-changed')) }
    catch (e) { toast.error(apiMessage(e)) }
  }

  const programNamesOf = (c) => (c.programNames?.length ? c.programNames.join(', ') : (c.programName || '—'))

  return (
    <div>
      <PageHeader icon={BookOpen} title="Courses" subtitle="Manage the course catalogue and faculty assignments."
        actions={canManage && <Button onClick={() => setOpen(true)}><Plus size={16} /> New course</Button>} />

      <Tabs active={tab} onChange={(t) => { setTab(t); setScoped(null) }} tabs={[
        { key: 'all', label: 'All courses' },
        { key: 'program', label: 'By program' },
        { key: 'faculty', label: 'By faculty' },
      ]} />

      {tab !== 'all' && (
        <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
          {tab === 'program' ? (
            <>
              <div className="w-56"><span className="label">Program</span>
                <Select value={q.programId} onChange={(e) => setQ({ ...q, programId: e.target.value })}
                  placeholder="Select a program"
                  options={programs.map((p) => ({ value: p.programId, label: p.programName }))} />
              </div>
              <div className="w-36"><span className="label">Semester</span><Input type="number" value={q.semester} onChange={(e) => setQ({ ...q, semester: e.target.value })} placeholder="Optional" /></div>
            </>
          ) : (
            <div className="w-56"><span className="label">Faculty</span>
              <FacultySelect value={q.facultyId} onChange={(id) => setQ({ ...q, facultyId: id })} />
            </div>
          )}
          <Button onClick={runScope} loading={scopeLoading}><Search size={16} /> Search</Button>
        </Card>
      )}

      <Card className="p-4">
        {(loading || scopeLoading) ? <Spinner /> : list.length === 0 ? (
          <EmptyState icon={BookOpen} title={tab === 'all' ? 'No courses yet' : 'No results'}
            hint={tab === 'all' ? 'Create a course to build your catalogue.' : 'Adjust the search above.'} />
        ) : (
          <Table head={['ID', 'Code', 'Name', 'Programs', 'Credits', 'Sem', 'Faculty', 'Status', ...(canManage ? ['Actions'] : [])]}>
            {list.map((c) => (
              <Row key={c.courseId}>
                <Cell mono>{c.courseId}</Cell>
                <Cell mono>{c.courseCode}</Cell>
                <Cell><span className="font-medium">{c.courseName}</span></Cell>
                <Cell>{programNamesOf(c)}</Cell>
                <Cell>{c.credits}</Cell>
                <Cell>{c.semester}</Cell>
                <Cell>{c.facultyName ?? c.facultyId ?? '—'}</Cell>
                <Cell><Badge value={c.status} /></Cell>
                {canManage && (
                  <Cell>
                    <div className="flex items-center gap-1.5">
                      <button title="Assign faculty" onClick={() => setAssign({ id: c.courseId, facultyId: '' })}
                        className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-400"><UserPlus size={15} /></button>
                      <Select className="field !py-1 !text-xs !w-28" value={c.status || ''}
                        onChange={(e) => updateStatus(c.courseId, e.target.value)} options={COURSE_STATUS} placeholder="Status…" />
                    </div>
                  </Cell>
                )}
              </Row>
            ))}
          </Table>
        )}
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title="Create course">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Course name"><Input value={form.courseName} onChange={set('courseName')} placeholder="Data Structures" /></Field>
            <Field label="Course code"><Input value={form.courseCode} onChange={set('courseCode')} placeholder="CS201" /></Field>
          </div>
          <Field label="Programs" hint="A course can belong to multiple programs (many-to-many).">
            <ProgramMultiSelect programs={programs} selected={form.programIds} onChange={(ids) => setForm({ ...form, programIds: ids })} />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Credits"><Input type="number" value={form.credits} onChange={set('credits')} /></Field>
            <Field label="Semester"><Input type="number" value={form.semester} onChange={set('semester')} /></Field>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Faculty" hint="Optional — choose a registered faculty">
              <FacultySelect value={form.facultyId} onChange={(id) => setForm({ ...form, facultyId: id ?? '' })} />
            </Field>
            <Field label="Max enrollment"><Input type="number" value={form.maxEnrollment} onChange={set('maxEnrollment')} /></Field>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={create} loading={saving}>Create course</Button>
          </div>
        </div>
      </Modal>

      <Modal open={!!assign} onClose={() => setAssign(null)} title="Assign faculty" size="sm">
        <div className="space-y-4">
          <Field label="Faculty">
            <FacultySelect value={assign?.facultyId || ''} onChange={(id) => setAssign({ ...assign, facultyId: id ?? '' })} />
          </Field>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setAssign(null)}>Cancel</Button>
            <Button onClick={doAssign}>Assign</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
