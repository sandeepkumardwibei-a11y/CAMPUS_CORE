import { useState } from 'react'
import { Layers, Plus } from 'lucide-react'
import { ProgramApi, DepartmentApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { PROGRAM_LEVELS, PROGRAM_STATUS, can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Modal, Field, Input, Select,
} from '../components/ui'

const empty = { programName: '', level: 'UG', durationYears: 4, totalSeats: 60, minimumPercentage: 60, departmentId: '' }

export default function Programs() {
  const toast = useToast()
  const { user } = useAuth()
  const canManage = can(user?.role, 'prog.create')
  const { data, loading, reload } = useAsync(() => ProgramApi.all(), [])
  // Departments are needed for the create dropdown (item 3)
  const { data: deptData } = useAsync(() => (canManage ? DepartmentApi.all() : Promise.resolve([])), [canManage])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const programs = asArray(data)
  const departments = asArray(deptData)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const create = async () => {
    if (!form.departmentId) { toast.error('Please select a department'); return }
    setSaving(true)
    try {
      await ProgramApi.create({
        programName: form.programName,
        level: form.level,
        durationYears: Number(form.durationYears),
        totalSeats: Number(form.totalSeats),
        minimumPercentage: Number(form.minimumPercentage),
        departmentId: Number(form.departmentId), // item 3: one department per program
      })
      toast.success('Program created')
      setOpen(false); setForm(empty); reload()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  const updateStatus = async (id, status) => {
    try { await ProgramApi.updateStatus(id, status); toast.success('Status updated'); reload(); window.dispatchEvent(new Event('cc:data-changed')) }
    catch (e) { toast.error(apiMessage(e)) }
  }

  return (
    <div>
      <PageHeader icon={Layers} title="Programs" subtitle="Academic programs offered across departments."
        actions={canManage && <Button onClick={() => setOpen(true)}><Plus size={16} /> New program</Button>} />

      <Card className="p-4">
        {loading ? <Spinner /> : programs.length === 0 ? (
          <EmptyState icon={Layers} title="No programs yet" hint={canManage ? 'Create your first academic program to get started.' : 'No programs have been published yet.'}
            action={canManage && <Button onClick={() => setOpen(true)}><Plus size={16} /> New program</Button>} />
        ) : (
          <Table head={['ID', 'Name', 'Department', 'Level', 'Duration', 'Seats', 'Min %', 'Status', ...(canManage ? ['Set status'] : [])]}>
            {programs.map((p) => (
              <Row key={p.programId}>
                <Cell mono>{p.programId}</Cell>
                <Cell><span className="font-medium">{p.programName}</span></Cell>
                <Cell>{p.departmentName || '—'}</Cell>
                <Cell><Badge value={p.level} /></Cell>
                <Cell>{p.durationYears} yrs</Cell>
                <Cell>{p.totalSeats}</Cell>
                <Cell>{p.minimumPercentage}%</Cell>
                <Cell><Badge value={p.status} /></Cell>
                {canManage && (
                  <Cell>
                    <Select className="field !py-1 !text-xs" value={p.status || ''}
                      onChange={(e) => updateStatus(p.programId, e.target.value)} options={PROGRAM_STATUS} placeholder="Set…" />
                  </Cell>
                )}
              </Row>
            ))}
          </Table>
        )}
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title="Create program">
        <div className="space-y-4">
          <Field label="Program name"><Input value={form.programName} onChange={set('programName')} placeholder="B.Tech Computer Science" /></Field>
          <Field label="Department" hint="One department can offer many programs; a program belongs to one department.">
            <Select value={form.departmentId} onChange={set('departmentId')} placeholder="Select a department"
              options={departments.map((d) => ({ value: d.departmentId, label: `${d.departmentName} (ID: ${d.departmentId})` }))} />
          </Field>
          <Field label="Level"><Select options={PROGRAM_LEVELS} value={form.level} onChange={set('level')} /></Field>
          <div className="grid grid-cols-3 gap-4">
            <Field label="Duration (yrs)"><Input type="number" value={form.durationYears} onChange={set('durationYears')} /></Field>
            <Field label="Total seats"><Input type="number" value={form.totalSeats} onChange={set('totalSeats')} /></Field>
            <Field label="Min %"><Input type="number" value={form.minimumPercentage} onChange={set('minimumPercentage')} /></Field>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={create} loading={saving}>Create program</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
