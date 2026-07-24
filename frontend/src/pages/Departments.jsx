import { useState } from 'react'
import { Building2, Plus } from 'lucide-react'
import { DepartmentApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import {
  PageHeader, Card, Button, Field, Input, Badge,
  Table, Row, Cell, Spinner, EmptyState,
} from '../components/ui'

export default function Departments() {
  const toast = useToast()
  const { user } = useAuth()
  const canManage = can(user?.role, 'dept.create') // ADMIN only

  const { data, loading, reload } = useAsync(() => DepartmentApi.all(), [])
  const departments = asArray(data)

  const [form, setForm] = useState({ departmentName: '' })
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const create = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      // item 2: departments are created by name only — no program ID.
      await DepartmentApi.create({ departmentName: form.departmentName })
      toast.success('Department created')
      setForm({ departmentName: '' })
      reload()
    } catch (err) {
      toast.error(apiMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <PageHeader
        icon={Building2}
        title="Departments"
        subtitle="The departments that own programs and courses."
      />

      {canManage && (
        <Card className="p-6 mb-6">
          <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>New department</h3>
          <form onSubmit={create} className="grid sm:grid-cols-2 gap-4 items-end">
            <Field label="Department name">
              <Input required value={form.departmentName} onChange={set('departmentName')} placeholder="Computer Science" />
            </Field>
            <div className="sm:col-span-2">
              <Button type="submit" loading={saving}><Plus size={16} /> Create department</Button>
            </div>
          </form>
        </Card>
      )}

      <Card className="p-4">
        <h3 className="font-display font-semibold mb-4 px-2" style={{ color: 'var(--text)' }}>All departments</h3>
        {loading ? (
          <Spinner />
        ) : departments.length === 0 ? (
          <EmptyState
            icon={Building2}
            title="No departments yet"
            hint={canManage ? 'Create the first department using the form above.' : 'No departments have been published yet.'}
          />
        ) : (
          <Table head={['ID', 'Name', 'Status']}>
            {departments.map((d) => (
              <Row key={d.departmentId}>
                <Cell mono>{d.departmentId}</Cell>
                <Cell><span className="font-medium">{d.departmentName}</span></Cell>
                <Cell><Badge value={d.status || 'ACTIVE'} /></Cell>
              </Row>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}
