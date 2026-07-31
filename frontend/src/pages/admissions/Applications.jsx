import { useNavigate } from 'react-router-dom'
import { ClipboardList, RefreshCw, ArrowRight } from 'lucide-react'
import { AdmissionApi } from '../../lib/services'
import { useAsync, asArray } from '../../lib/hooks'
import { apiMessage } from '../../lib/api'
import { can } from '../../lib/constants'
import { useAuth } from '../../context/AuthContext'
import { PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState } from '../../components/ui'

export default function Applications() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const allowed = can(user?.role, 'adm.viewAll')
  const { data, loading, error, reload } = useAsync(() => (allowed ? AdmissionApi.all() : Promise.resolve([])), [allowed])
  const applications = asArray(data)

  if (!allowed) {
    return <Card className="p-4"><EmptyState icon={ClipboardList} title="Restricted" hint="The applications list is available to administrators only." /></Card>
  }

  return (
    <div>
      <PageHeader icon={ClipboardList} title="Applications"
        subtitle="Every admission application currently in progress — enrolled applicants are moved off this list."
        actions={<Button variant="outline" onClick={reload} loading={loading}><RefreshCw size={15} /> Refresh</Button>} />

      <Card className="p-4">
        {loading ? <Spinner /> : error ? (
          <EmptyState icon={ClipboardList} title="Couldn't load applications" hint={apiMessage(error)} />
        ) : applications.length === 0 ? (
          <EmptyState icon={ClipboardList} title="No applications in progress" hint="New applications will show up here once submitted." />
        ) : (
          <Table head={['Application ID', 'Name', 'Email', 'Program', 'Status', '']}>
            {applications.map((a) => (
              <Row key={a.applicationId}>
                <Cell mono>{a.applicationId}</Cell>
                <Cell>{a.applicantName}</Cell>
                <Cell>{a.email}</Cell>
                <Cell>{a.programName || '—'}</Cell>
                <Cell><Badge value={a.status} /></Cell>
                <Cell>
                  <Button variant="subtle" size="sm" onClick={() => navigate(`/admissions/${a.applicationId}`)}>
                    Open <ArrowRight size={14} />
                  </Button>
                </Cell>
              </Row>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}
