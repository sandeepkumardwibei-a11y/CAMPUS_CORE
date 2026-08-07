import { useState, useCallback, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, RefreshCw, Plus, Trash2, Save, Send, CheckCircle2, Ban } from 'lucide-react'
import { ExamApi } from '../../lib/services'
import { asArray } from '../../lib/hooks'
import { apiMessage } from '../../lib/api'
import { useToast } from '../../context/ToastContext'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Field, Input, Modal,
} from '../../components/ui'
import { FileSpreadsheet } from 'lucide-react'
import { FacultySelect } from '../../components/ui/FacultySelect'
import { StudentSelect } from '../../components/ui/StudentSelect'
import { can } from '../../lib/constants'
import { useAuth } from '../../context/AuthContext'

export default function ExamDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const canGrade = can(user?.role, 'exam.enterGrades')
  const canMarkConducted = can(user?.role, 'exam.markConducted')
  const canPublish = can(user?.role, 'exam.publish')
  const canCancel = can(user?.role, 'exam.cancel')
  const [exam, setExam] = useState(null)
  const [grades, setGrades] = useState(null)
  const [loading, setLoading] = useState(true)
  const [facultyId, setFacultyId] = useState('')
  const [records, setRecords] = useState([{ studentId: '', marksObtained: '' }])
  const [saving, setSaving] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [marking, setMarking] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)

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
    const recs = records.filter((r) => r.studentId).map((r) => ({ studentId: Number(r.studentId), marksObtained: Number(r.marksObtained) }))
    const maxMarks = exam?.maxMarks != null ? Number(exam.maxMarks) : null
    const invalid = recs.find((r) => Number.isNaN(r.marksObtained) || r.marksObtained < 0 || (maxMarks != null && r.marksObtained > maxMarks))
    if (invalid) {
      toast.error(maxMarks != null
        ? `Marks must be between 0 and ${maxMarks} (max marks for this exam).`
        : 'Marks must be 0 or greater.')
      return
    }
    setSaving(true)
    try {
      await ExamApi.enterGrades(id, Number(facultyId), recs)
      toast.success('Grades saved'); setRecords([{ studentId: '', marksObtained: '' }]); load()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }
  const markConducted = async () => {
    setMarking(true)
    try { await ExamApi.markConducted(id); toast.success('Exam marked as conducted'); load() }
    catch (e) { toast.error(apiMessage(e)) } finally { setMarking(false) }
  }
  const publish = async () => {
    setPublishing(true)
    try { await ExamApi.publish(id); toast.success('Grades published'); load() }
    catch (e) { toast.error(apiMessage(e)) } finally { setPublishing(false) }
  }
  const cancelExam = async () => {
    setCancelling(true)
    try { await ExamApi.cancel(id); toast.success('Exam cancelled'); setConfirmCancel(false); load() }
    catch (e) { toast.error(apiMessage(e)) } finally { setCancelling(false) }
  }

  return (
    <div>
      <button onClick={() => navigate('/exams')} className="flex items-center gap-1.5 text-sm mb-4 hover:text-emerald-400 transition" style={{ color: 'var(--text-muted)' }}>
        <ArrowLeft size={15} /> Back to exams
      </button>

      <PageHeader icon={FileSpreadsheet} title={`Exam #${id}`} subtitle="Enter and publish grades for this exam."
        actions={<>
          <Button variant="outline" onClick={load} loading={loading}><RefreshCw size={15} /> Refresh</Button>
          {canMarkConducted && exam?.status === 'SCHEDULED' && (
            <Button variant="outline" onClick={markConducted} loading={marking}><CheckCircle2 size={15} /> Mark conducted</Button>
          )}
          {canCancel && exam?.status === 'SCHEDULED' && (
            <Button variant="danger" onClick={() => setConfirmCancel(true)}><Ban size={15} /> Cancel exam</Button>
          )}
          {canPublish && <Button onClick={publish} loading={publishing} disabled={exam?.status !== 'CONDUCTED'}><Send size={15} /> Publish grades</Button>}
        </>} />

      <Modal open={confirmCancel} onClose={() => setConfirmCancel(false)} title="Cancel this exam?">
        <div className="space-y-4">
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            This will mark the exam as CANCELLED. It can no longer be conducted, graded, or published. This cannot be undone.
          </p>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setConfirmCancel(false)}>Go back</Button>
            <Button variant="danger" onClick={cancelExam} loading={cancelling}><Ban size={15} /> Yes, cancel exam</Button>
          </div>
        </div>
      </Modal>

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
              {exam?.status !== 'CONDUCTED' && (
                <p className="text-xs mb-4 rounded-lg p-3" style={{ background: 'var(--surface-2, rgba(120,120,140,0.08))', color: 'var(--text-muted)' }}>
                  Grades can only be entered once the exam controller marks this exam as conducted.
                </p>
              )}
              <Field label="Faculty" className="mb-4"><FacultySelect value={facultyId} onChange={(id) => setFacultyId(id ?? '')} /></Field>
              <div className="space-y-2 mb-4">
                <div className="flex gap-2 px-1">
                  <span className="text-xs font-semibold flex-1" style={{ color: 'var(--text-faint)' }}>Student</span>
                  <span className="text-xs font-semibold w-28" style={{ color: 'var(--text-faint)' }}>Marks</span>
                  <span className="w-9 shrink-0" />
                </div>
                {records.map((r, i) => (
                  <div key={i} className="flex gap-2 items-center">
                    <div className="flex-5 min-w-500">
                      <StudentSelect
                        courseId={exam?.courseId}
                        value={r.studentId}
                        onChange={(id) => upd(i, 'studentId', id ?? '')}
                      />
                    </div>
                    <input type="number" min={0} max={exam?.maxMarks ?? undefined} step="0.01" className="field w-28 shrink-50" value={r.marksObtained} onChange={(e) => upd(i, 'marksObtained', e.target.value)} placeholder={exam?.maxMarks != null ? `0–${exam.maxMarks}` : 'Marks'} />
                    <button onClick={() => setRecords((rs) => rs.filter((_, idx) => idx !== i))} className="p-2 rounded-lg hover:bg-rose-500/10 text-rose-500 shrink-0"><Trash2 size={16} /></button>
                  </div>
                ))}
                <Button variant="subtle" size="sm" onClick={() => setRecords((r) => [...r, { studentId: '', marksObtained: '' }])}><Plus size={14} /> Add student</Button>
              </div>
              <Button onClick={enterGrades} loading={saving} disabled={!facultyId || exam?.status !== 'CONDUCTED'}><Save size={16} /> Save grades</Button>
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
