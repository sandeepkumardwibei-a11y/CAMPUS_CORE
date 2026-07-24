import { useState } from 'react'
import { UserCog, Search } from 'lucide-react'
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

  const changeStatus = async (id, status) => {
    try {
      await UserApi.updateStatus(id, status)
      toast.success(`Status updated to ${status}`)
      load()
    } catch (e) {
      toast.error(apiMessage(e))
    }
  }

  return (
    <div>
      <PageHeader icon={UserCog} title="Users" subtitle="Browse accounts and manage their status." />

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
        ) : users.length === 0 ? (
          <EmptyState icon={UserCog} title="No users found" hint="Try a different role filter." />
        ) : (
          <Table head={canManage ? ['ID', 'Name', 'Email', 'Role', 'Status', 'Set status'] : ['ID', 'Name', 'Email', 'Role', 'Status']}>
            {users.map((u) => (
              <Row key={u.id ?? u.userId}>
                <Cell mono>{u.id ?? u.userId}</Cell>
                <Cell>{u.name}</Cell>
                <Cell>{u.email}</Cell>
                <Cell><Badge value={u.role} /></Cell>
                <Cell><Badge value={u.status} /></Cell>
                {canManage && (
                  <Cell>
                    <Select className="field !py-1 !text-xs" value={u.status || ''}
                      onChange={(e) => changeStatus(u.id ?? u.userId, e.target.value)}
                      options={USER_STATUS} placeholder="Set status…" />
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
