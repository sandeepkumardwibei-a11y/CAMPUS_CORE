import { useState, useEffect } from 'react'
import { Award, Search } from 'lucide-react'
import { AcademicStandingApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import {
  PageHeader, Card, Button, Field, Input, Badge,
  Table, Row, Cell, Spinner, EmptyState,
} from '../components/ui'

// Ranking pill tones
const RANK_TONE = {
  EXCELLENT: 'bg-emerald-500/15 text-emerald-500 ring-emerald-500/30',
  GOOD: 'bg-sky-500/15 text-sky-500 ring-sky-500/30',
  AVERAGE: 'bg-amber-500/15 text-amber-500 ring-amber-500/30',
  POOR: 'bg-rose-500/15 text-rose-500 ring-rose-500/30',
  NOT_AVAILABLE: 'bg-slate-500/15 text-slate-400 ring-slate-500/30',
}

function RankBadge({ value }) {
  const v = String(value || 'NOT_AVAILABLE').toUpperCase()
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-semibold ring-1 ${RANK_TONE[v] || RANK_TONE.NOT_AVAILABLE}`}>
      {v.replace(/_/g, ' ')}
    </span>
  )
}

// ADMIN / FACULTY: whole cohort. STUDENT: only their own record.
export default function AcademicStanding() {
  const { user } = useAuth()
  const toast = useToast()
  const viewAll = can(user?.role, 'standing.viewAll')

  if (viewAll) return <AllStandings />
  return <MyStanding userId={user?.userId} toast={toast} />
}

function AllStandings() {
  const { data, loading } = useAsync(() => AcademicStandingApi.all(), [])
  const rows = asArray(data)

  return (
    <div>
      <PageHeader icon={Award} title="Academic Standing"
        subtitle="Automatic ranking of every student based on CGPA." />
      <Card className="p-4">
        {loading ? <Spinner /> : rows.length === 0 ? (
          <EmptyState icon={Award} title="No standings yet" hint="Once result cards are published, rankings appear here." />
        ) : (
          <Table head={['Rank', 'Student', 'ID', 'Year', 'Sem', 'CGPA', 'Standing', 'Remark']}>
            {rows.map((r, i) => (
              <Row key={r.studentId}>
                <Cell mono>{i + 1}</Cell>
                <Cell><span className="font-medium">{r.studentName}</span></Cell>
                <Cell mono>{r.studentId}</Cell>
                <Cell>{r.academicYear || '—'}</Cell>
                <Cell>{r.semester ?? '—'}</Cell>
                <Cell mono>{r.cgpa ?? '—'}</Cell>
                <Cell><RankBadge value={r.ranking} /></Cell>
                <Cell>{r.remark}</Cell>
              </Row>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}

function MyStanding({ userId, toast }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!userId) { setLoading(false); return }
    AcademicStandingApi.forStudent(userId)
      .then(setData)
      .catch((e) => toast.error(apiMessage(e)))
      .finally(() => setLoading(false))
  }, [userId])

  return (
    <div>
      <PageHeader icon={Award} title="My Academic Standing"
        subtitle="Your automatic ranking based on your CGPA." />
      <Card className="p-6 max-w-xl">
        {loading ? <Spinner /> : !data ? (
          <EmptyState icon={Award} title="No standing yet" hint="Your ranking appears once your results are published." />
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-display font-semibold text-lg" style={{ color: 'var(--text)' }}>{data.studentName}</p>
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                  {data.academicYear ? `${data.academicYear} · Semester ${data.semester}` : 'No published results yet'}
                </p>
              </div>
              <RankBadge value={data.ranking} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="glass rounded-xl p-4">
                <p className="text-xs" style={{ color: 'var(--text-faint)' }}>CGPA</p>
                <p className="text-2xl font-bold font-display" style={{ color: 'var(--text)' }}>{data.cgpa ?? '—'}</p>
              </div>
              <div className="glass rounded-xl p-4">
                <p className="text-xs" style={{ color: 'var(--text-faint)' }}>SGPA</p>
                <p className="text-2xl font-bold font-display" style={{ color: 'var(--text)' }}>{data.sgpa ?? '—'}</p>
              </div>
            </div>
            <div className="rounded-xl p-4 bg-emerald-500/5 border border-emerald-500/20">
              <p className="text-sm" style={{ color: 'var(--text)' }}>{data.remark}</p>
            </div>
          </div>
        )}
      </Card>
    </div>
  )
}
