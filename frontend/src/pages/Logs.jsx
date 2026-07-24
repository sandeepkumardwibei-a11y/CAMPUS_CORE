import { useState } from 'react'
import { ScrollText } from 'lucide-react'
import { LogApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import {
  PageHeader, Card, Table, Row, Cell, Spinner, EmptyState,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'

function fmt(ts) {
  if (!ts) return '—'
  try { return new Date(ts).toLocaleString('en-IN') } catch { return String(ts) }
}

export default function Logs() {
  const { user } = useAuth()
  const allowed = can(user?.role, 'logs.view')
  const [tab, setTab] = useState('audit')

  const { data: auditData, loading: auditLoading } = useAsync(() => (allowed ? LogApi.audit() : Promise.resolve([])), [allowed])
  const { data: moduleData, loading: moduleLoading } = useAsync(() => (allowed ? LogApi.module() : Promise.resolve([])), [allowed])

  if (!allowed) {
    return <Card className="p-4"><EmptyState icon={ScrollText} title="Restricted" hint="System logs are available to administrators only." /></Card>
  }

  const audit = asArray(auditData)
  const modules = asArray(moduleData)

  return (
    <div>
      <PageHeader icon={ScrollText} title="System Logs" subtitle="Audit and module activity across CampusCore." />

      <Tabs active={tab} onChange={setTab} tabs={[
        { key: 'audit', label: `Audit Log (${audit.length})` },
        { key: 'module', label: `Module Log (${modules.length})` },
      ]} />

      <Card className="p-4">
        {tab === 'audit' ? (
          auditLoading ? <Spinner /> : audit.length === 0 ? (
            <EmptyState icon={ScrollText} title="No audit entries" hint="Audit activity will appear here." />
          ) : (
            <Table head={['ID', 'User', 'Action', 'Module', 'Timestamp']}>
              {audit.map((a) => (
                <Row key={a.auditId}>
                  <Cell mono>{a.auditId}</Cell>
                  <Cell>{a.userName || a.userId || '—'}</Cell>
                  <Cell>{a.action}</Cell>
                  <Cell>{a.module || '—'}</Cell>
                  <Cell>{fmt(a.timestamp)}</Cell>
                </Row>
              ))}
            </Table>
          )
        ) : (
          moduleLoading ? <Spinner /> : modules.length === 0 ? (
            <EmptyState icon={ScrollText} title="No module entries" hint="Module activity will appear here." />
          ) : (
            <Table head={['ID', 'Module', 'Action Performed', 'Accessed By', 'User ID', 'Timestamp']}>
              {modules.map((m) => (
                <Row key={m.logId}>
                  <Cell mono>{m.logId}</Cell>
                  <Cell>{m.moduleName}</Cell>
                  <Cell>{m.actionPerformed}</Cell>
                  <Cell>{m.accessedBy}</Cell>
                  <Cell mono>{m.userId ?? '—'}</Cell>
                  <Cell>{fmt(m.timestamp)}</Cell>
                </Row>
              ))}
            </Table>
          )
        )}
      </Card>
    </div>
  )
}
