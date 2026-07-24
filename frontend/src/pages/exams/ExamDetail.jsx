import { useState, useCallback, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, RefreshCw, Plus, Trash2, Save, Send } from 'lucide-react'
import { ExamApi } from '../../lib/services'
import { asArray } from '../../lib/hooks'
import { apiMessage } from '../../lib/api'
import { useToast } from '../../context/ToastContext'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Field, Input,
} from '../../components/ui'
import { FileSpreadsheet } from 'lucide-react'
import { FacultySelect } from '../../components/ui/FacultySelect'
import { can } from '../../lib/constants'
import { useAuth } from '../../context/AuthContext'

export default function ExamDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const canGrade = can(user?.role, 'exam.enterGrades')
  const canPublish = can(user?.role, 'exam.publish')
  const [exam, setExam] = useState(null)
  const [grades, setGrades] = useState(null)
  const [loading, setLoading] = useState(true)
  const [facultyId, setFacultyId] = useState('')
  const [records, setRecords] = useState([{ studentId: '', marksObtained: '' }])
  const [saving, setSaving] = useState(false)
  const [publishing, setPublishing] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [x, g] = await Promise.all([
        ExamApi.byId(id).catch(() => null),
        ExamApi.examGrades(id).catch(() => []),
      ])
      setExam(x); setGrades(asArray(g))
    } catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }, [id, toast])

  useEffect(() => { load() }, [load])

  const upd = (i, k, v) => setRecords((r) => r.map((x, idx) => (idx === i ? { ...x, [k]: v } : x)))

  const enterGrades = async () => {
    setSaving(true)
    try {
      const recs = records.filter((r) => r.studentId).map((r) => ({ studentId: Number(r.studentId), marksObtained: Number(r.marksObtained) }))
      await ExamApi.enterGrades(id, Number(facultyId), recs)
      toast.success('Grades saved'); setRecords([{ studentId: '', marksObtained: '' }]); load()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }
  const publish = async () => {
    setPublishing(true)
    try { await ExamApi.publish(id); toast.success('Grades published'); load() }
    catch (e) { toast.error(apiMessage(e)) } finally { setPublishing(false) }
  }

  return (
    <div>
      <button onClick={() => navigate('/exams')} className="flex items-center gap-1.5 text-sm mb-4 hover:text-emerald-400 transition" style={{ color: 'var(--text-muted)' }}>
        <ArrowLeft size={15} /> Back to exams
      </button>

      <PageHeader icon={FileSpreadsheet} title={`Exam #${id}`} subtitle="Enter and publish grades for this exam."
        actions={<>
          <Button variant="outline" onClick={load} loading={loading}><RefreshCw size={15} /> Refresh</Button>
          {canPublish && <Button onClick={publish} loading={publishing}><Send size={15} /> Publish grades</Button>}
        </>} />

      {loading ? <Spinner /> : (
        <>
          {exam && typeof exam === 'object' && (
            <Card className="p-6 mb-6">
              <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4 text-sm">
                {[
                  ['Course', exam.courseName || exam.courseId], ['Type', exam.examType],
                  ['Date', exam.examDate], ['Start', exam.startTime], ['Duration', exam.durationMins && `${exam.durationMins} min`],
                  ['Venue', exam.venue], ['Max marks', exam.maxMarks], ['Status', exam.status],
                ].filter(([, v]) => v != null).map(([k, v]) => (
                  <div key={k}>
                    <p className="text-xs mb-0.5" style={{ color: 'var(--text-faint)' }}>{k}</p>
                    {k === 'Status' || k === 'Type' ? <Badge value={v} /> : <p style={{ color: 'var(--text)' }}>{String(v)}</p>}
                  </div>
                ))}
              </div>
            </Card>
          )}

          <div className={`grid gap-6 ${canGrade ? 'lg:grid-cols-2' : 'lg:grid-cols-1'}`}>
            {/* Enter grades */}
            {canGrade && (
            <Card className="p-6">
              <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>Enter grades</h3>
              <Field label="Faculty" className="mb-4"><FacultySelect value={facultyId} onChange={(id) => setFacultyId(id ?? '')} /></Field>
              <div className="space-y-2 mb-4">
                <div className="flex gap-2 px-1">
                  <span className="text-xs font-semibold flex-1" style={{ color: 'var(--text-faint)' }}>Student ID</span>
                  <span className="text-xs font-semibold w-28" style={{ color: 'var(--text-faint)' }}>Marks</span>
                  <span className="w-9 shrink-0" />
                </div>
                {records.map((r, i) => (
                  <div key={i} className="flex gap-2 items-center">
                    <input type="number" className="field flex-2 min-w-5" value={r.studentId} onChange={(e) => upd(i, 'studentId', e.target.value)}  />
                    <input type="number" className="field w-28 shrink-10" value={r.marksObtained} onChange={(e) => upd(i, 'marksObtained', e.target.value)} placeholder="Marks" />
                    <button onClick={() => setRecords((rs) => rs.filter((_, idx) => idx !== i))} className="p-2 rounded-lg hover:bg-rose-500/10 text-rose-500 shrink-0"><Trash2 size={16} /></button>
                  </div>
                ))}
                <Button variant="subtle" size="sm" onClick={() => setRecords((r) => [...r, { studentId: '', marksObtained: '' }])}><Plus size={14} /> Add student</Button>
              </div>
              <Button onClick={enterGrades} loading={saving} disabled={!facultyId}><Save size={16} /> Save grades</Button>
            </Card>
            )}

            {/* Current grades */}
            <Card className="p-6">
              <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>Recorded grades</h3>
              {!grades || grades.length === 0 ? <EmptyState icon={FileSpreadsheet} title="No grades yet" hint="Enter grades on the left to populate this list." />
                : <Table head={['Student', 'Marks', 'Grade', 'Status']}>
                    {grades.map((g, i) => (
                      <Row key={i}>
                        <Cell>{g.studentName || g.studentId}</Cell>
                        <Cell>{g.marksObtained ?? g.marks ?? '—'}</Cell>
                        <Cell>{g.grade ?? '—'}</Cell>
                        <Cell><Badge value={g.status} /></Cell>
                      </Row>
                    ))}
                  </Table>}
            </Card>
          </div>
        </>
      )}
    </div>
  )
}
