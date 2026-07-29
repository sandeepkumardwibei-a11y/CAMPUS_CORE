import { useState } from 'react'
import { CalendarClock, Plus, Search } from 'lucide-react'
import { BookingApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { BOOKING_STATUS, can } from '../lib/constants'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Modal, Field, Input, Select,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'

export default function Bookings() {
  const toast = useToast()
  const { user } = useAuth()
  const canViewAll = can(user?.role, 'booking.all')
  const canManage = can(user?.role, 'booking.updateStatus')
  const [tab, setTab] = useState(canViewAll ? 'all' : 'user')
  const { data, loading, reload } = useAsync(
    () => (canViewAll ? BookingApi.all() : Promise.resolve([])), [])
  const [scoped, setScoped] = useState(null)
  const [scopeLoading, setScopeLoading] = useState(false)
  const [userId, setUserId] = useState(user?.userId || '')
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({ userId: user?.userId || '', facilityName: '', bookingDate: '', startTime: '', endTime: '', purpose: '' })
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const list = tab === 'all' ? asArray(data) : asArray(scoped)

  const loadByUser = async () => {
    if (!userId) return toast.error('Enter a user ID')
    setScopeLoading(true)
    try { setScoped(await BookingApi.byUser(Number(userId))) }
    catch (e) { toast.error(apiMessage(e)) } finally { setScopeLoading(false) }
  }
  const book = async () => {
    setSaving(true)
    try {
      await BookingApi.book(Number(form.userId), {
        facilityName: form.facilityName, bookingDate: form.bookingDate,
        startTime: form.startTime, endTime: form.endTime, purpose: form.purpose,
      })
      toast.success('Facility booked'); setOpen(false)
      setForm({ userId: user?.userId || '', facilityName: '', bookingDate: '', startTime: '', endTime: '', purpose: '' })
      tab === 'all' && canViewAll ? reload() : loadByUser()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }
  const updateStatus = async (id, status) => {
    try { await BookingApi.updateStatus(id, status); toast.success(`Marked ${status.toLowerCase()}`); (tab === 'all' && canViewAll) ? reload() : loadByUser() }
    catch (e) { toast.error(apiMessage(e)) }
  }

  return (
    <div>
      <PageHeader icon={CalendarClock} title="Facility Bookings"
        subtitle={canManage ? 'Reserve halls and facilities, and manage requests.' : 'Reserve halls and facilities and track your requests.'}
        actions={<Button onClick={() => setOpen(true)}><Plus size={16} /> New booking</Button>} />

      <Tabs active={tab} onChange={(t) => { setTab(t); setScoped(null) }} tabs={[
        ...(canViewAll ? [{ key: 'all', label: 'All bookings' }] : []),
        { key: 'user', label: 'By user' },
      ]} />

      {tab === 'user' && (
        <Card className="p-4 mb-5 flex items-end gap-3">
          <div className="w-40"><span className="label">User ID</span><Input type="number" min={1} max={999999} value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="5" /></div>
          <Button onClick={loadByUser} loading={scopeLoading}><Search size={16} /> Load</Button>
        </Card>
      )}

      <Card className="p-4">
        {(loading || scopeLoading) ? <Spinner /> : (tab === 'user' && !scoped) ? (
          <EmptyState icon={CalendarClock} title="Load bookings" hint="Enter a user ID and load their bookings." />
        ) : list.length === 0 ? (
          <EmptyState icon={CalendarClock} title="No bookings" hint="Create a booking to reserve a facility." />
        ) : (
          <Table head={['ID', 'Facility', 'Booked by', 'Date', 'Start', 'End', 'Purpose', 'Status', ...(canManage ? ['Set status'] : [])]}>
            {list.map((b) => (
              <Row key={b.bookingId}>
                <Cell mono>{b.bookingId}</Cell>
                <Cell><span className="font-medium">{b.facilityName}</span></Cell>
                <Cell>{b.bookedByName || b.bookedById || '—'}</Cell>
                <Cell>{b.bookingDate}</Cell>
                <Cell>{b.startTime}</Cell>
                <Cell>{b.endTime}</Cell>
                <Cell>{b.purpose}</Cell>
                <Cell><Badge value={b.status} /></Cell>
                {canManage && (
                  <Cell>
                    <Select className="field !py-1 !text-xs !w-32" value={b.status || ''}
                      onChange={(e) => updateStatus(b.bookingId, e.target.value)} options={BOOKING_STATUS} placeholder="Set…" />
                  </Cell>
                )}
              </Row>
            ))}
          </Table>
        )}
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title="Book a facility">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="User ID"><Input type="number" min={1} max={999999} value={form.userId} onChange={set('userId')} placeholder="5" /></Field>
            <Field label="Facility name"><Input value={form.facilityName} onChange={set('facilityName')} placeholder="Seminar Hall B" /></Field>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Field label="Date"><Input type="date" value={form.bookingDate} onChange={set('bookingDate')} /></Field>
            <Field label="Start time"><Input type="time" step="1" value={form.startTime} onChange={set('startTime')} /></Field>
            <Field label="End time"><Input type="time" step="1" value={form.endTime} onChange={set('endTime')} /></Field>
          </div>
          <Field label="Purpose"><Input value={form.purpose} onChange={set('purpose')} placeholder="Club meeting" /></Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={book} loading={saving}>Book facility</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
