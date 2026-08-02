import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { UserCog, Search, X } from 'lucide-react'
import { UserApi } from '../lib/services'
import { asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { ROLES, USER_STATUS, can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import {
  PageHeader, Card, Select, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
} from '../components/ui'

export default function Users() {
  const toast = useToast()
  const { user } = useAuth()
  const canManage = can(user?.role, 'users.updateStatus')
  const [searchParams, setSearchParams] = useSearchParams()
  // 🎯 Coming from the Dashboard's "Pending approvals" card links here as
  // /users?status=PENDING — pick that up and pre-filter the list to it.
  const statusFilter = searchParams.get('status') || ''
  const [role, setRole] = useState('')
  const [users, setUsers] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = async (r = role) => {
    setLoading(true)
    try {
      setUsers(asArray(await UserApi.list(r || undefined)))
    } catch (e) {
      toast.error(apiMessage(e))
    } finally {
      setLoading(false)
    }
  }

  // Auto-load once when arriving with a status filter already in the URL, so the
  // person doesn't have to click "Load users" themselves after following the link.
  useEffect(() => {
    if (statusFilter) load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const clearStatusFilter = () => {
    const next = new URLSearchParams(searchParams)
    next.delete('status')
    setSearchParams(next)
  }

  const changeStatus = async (id, status) => {
    try {
      await UserApi.updateStatus(id, status)
      toast.success(`Status updated to ${status}`)
      load()
    } catch (e) {
      toast.error(apiMessage(e))
    }
  }

  const displayedUsers = statusFilter
    ? (users || []).filter((u) => String(u?.status ?? '').toUpperCase() === statusFilter.toUpperCase())
    : users

  return (
    <div>
      <PageHeader icon={UserCog} title="Users" subtitle="Browse accounts and manage their status." />

      {statusFilter && (
        <Card className="p-3 mb-4 flex items-center justify-between gap-3">
          <span className="text-sm" style={{ color: 'var(--text)' }}>
            Showing only users with status <Badge value={statusFilter} />
          </span>
          <Button variant="ghost" onClick={clearStatusFilter}><X size={14} /> Clear filter</Button>
        </Card>
      )}

      <Card className="p-4 mb-6 flex flex-wrap items-end gap-3">
        <div className="w-full sm:w-56">
          <span className="label">Filter by role</span>
          <Select placeholder="All roles" options={ROLES} value={role} onChange={(e) => setRole(e.target.value)} />
        </div>
        <Button onClick={() => load()} loading={loading}><Search size={16} /> Load users</Button>
      </Card>

      <Card className="p-4">
        {loading ? <Spinner /> : !users ? (
          <EmptyState icon={UserCog} title="Load the directory" hint="Pick a role (or leave blank for everyone) and load users." />
        ) : displayedUsers.length === 0 ? (
          <EmptyState icon={UserCog} title="No users found" hint={statusFilter ? `No users with status ${statusFilter}.` : 'Try a different role filter.'} />
        ) : (
          <Table head={canManage ? ['ID', 'Name', 'Email', 'Role', 'Status', 'Set status'] : ['ID', 'Name', 'Email', 'Role', 'Status']}>
            {displayedUsers.map((u) => (
              <Row key={u.id ?? u.userId}>
                <Cell mono>{u.id ?? u.userId}</Cell>
                <Cell>{u.name}</Cell>
                <Cell>{u.email}</Cell>
                <Cell><Badge value={u.role} /></Cell>
                <Cell><Badge value={u.status} /></Cell>
                {canManage && (
                  <Cell>
                    {u.role === 'ADMIN' ? (
                      <span className="text-xs" style={{ color: 'var(--text-muted)' }}>—</span>
                    ) : (
                      <Select className="field !py-1 !text-xs" value={u.status || ''}
                        onChange={(e) => changeStatus(u.id ?? u.userId, e.target.value)}
                        options={u.role === 'STUDENT' ? USER_STATUS : USER_STATUS.filter((s) => s !== 'ALUMNI')} placeholder="Set status…" />
                    )}
                  </Cell>
                )}
              </Row>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}
