import { useState } from 'react'
import { BedDouble, Plus, Search, Check, X, IndianRupee, LogOut, CreditCard, ShieldCheck } from 'lucide-react'
import { HostelApi } from '../lib/services'
import { useAsync, asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { ROOM_TYPES, can } from '../lib/constants'
import { useAuth } from '../context/AuthContext'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Modal, Field, Input, Select,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'

export default function Hostel() {
  const toast = useToast()
  const { user } = useAuth()
  const role = user?.role
  const [tab, setTab] = useState('rooms')
  return (
    <div>
      <PageHeader icon={BedDouble} title="Hostel" subtitle="Rooms, applications, payments and allotments." />
      <Tabs active={tab} onChange={setTab} tabs={[
        { key: 'rooms', label: 'Rooms' },
        { key: 'apps', label: 'Applications' },
        { key: 'allot', label: 'Allotments' },
      ]} />
      {tab === 'rooms' && <Rooms toast={toast} role={role} />}
      {tab === 'apps' && <Applications toast={toast} role={role} />}
      {tab === 'allot' && <Allotments toast={toast} role={role} />}
    </div>
  )
}

function Rooms({ toast, role }) {
  const canManage = can(role, 'hostel.createRoom')
  const [onlyAvail, setOnlyAvail] = useState(false)
  const { data, loading, reload } = useAsync(() => (onlyAvail ? HostelApi.availableRooms() : HostelApi.rooms()), [onlyAvail])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ hostelBlock: '', roomNumber: '', capacity: 2, roomType: 'DOUBLE' })
  const [saving, setSaving] = useState(false)
  const rooms = asArray(data)

  const create = async () => {
    setSaving(true)
    try {
      await HostelApi.createRoom({ ...form, capacity: Number(form.capacity) })
      toast.success('Room created'); setOpen(false); setForm({ hostelBlock: '', roomNumber: '', capacity: 2, roomType: 'DOUBLE' }); reload()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <label className="flex items-center gap-2 text-sm cursor-pointer" style={{ color: 'var(--text-muted)' }}>
          <input type="checkbox" checked={onlyAvail} onChange={(e) => setOnlyAvail(e.target.checked)} className="accent-emerald-500 w-4 h-4" />
          Show available rooms only
        </label>
        {canManage && <Button onClick={() => setOpen(true)}><Plus size={16} /> New room</Button>}
      </div>
      <Card className="p-4">
        {loading ? <Spinner /> : rooms.length === 0 ? <EmptyState icon={BedDouble} title="No rooms" hint="Create rooms to start allotting." />
          : <Table head={['ID', 'Block', 'Room', 'Type', 'Capacity', 'Occupied', 'Status']}>
              {rooms.map((r) => (
                <Row key={r.roomId}>
                  <Cell mono>{r.roomId}</Cell><Cell>{r.hostelBlock}</Cell><Cell mono>{r.roomNumber}</Cell>
                  <Cell><Badge value={r.roomType} /></Cell><Cell>{r.capacity}</Cell>
                  <Cell>{r.occupiedCount ?? r.occupied ?? r.currentOccupancy ?? 0}</Cell>
                  <Cell><Badge value={r.status} /></Cell>
                </Row>
              ))}
            </Table>}
      </Card>
      <Modal open={open} onClose={() => setOpen(false)} title="Create room">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Hostel block"><Input value={form.hostelBlock} onChange={(e) => setForm({ ...form, hostelBlock: e.target.value })} placeholder="Block A" /></Field>
            <Field label="Room number"><Input value={form.roomNumber} onChange={(e) => setForm({ ...form, roomNumber: e.target.value })} placeholder="204" /></Field>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Capacity"><Input type="number" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} /></Field>
            <Field label="Room type"><Select options={ROOM_TYPES} value={form.roomType} onChange={(e) => setForm({ ...form, roomType: e.target.value })} /></Field>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={create} loading={saving}>Create</Button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function Applications({ toast, role }) {
  const canApply = can(role, 'hostel.apply')
  const canManage = can(role, 'hostel.approve')
  const canPay = can(role, 'hostel.pay')
  const [apply, setApply] = useState({ studentId: '', reason: '', roomType: 'DOUBLE' })
  const [applying, setApplying] = useState(false)
  const [appId, setAppId] = useState('')
  const [payOpen, setPayOpen] = useState(false)
  const [rejectTarget, setRejectTarget] = useState(null)
  const [rejectReason, setRejectReason] = useState('')
  const [busyId, setBusyId] = useState(null)

  const { data, loading, reload } = useAsync(() => (canManage ? HostelApi.allApplications() : Promise.resolve([])), [canManage])
  const applications = asArray(data)

  const doApply = async () => {
    setApplying(true)
    try {
      await HostelApi.apply(Number(apply.studentId), { reason: apply.reason, roomType: apply.roomType })
      toast.success('Application submitted'); setApply({ studentId: '', reason: '', roomType: 'DOUBLE' })
      reload()
    } catch (e) { toast.error(apiMessage(e)) } finally { setApplying(false) }
  }
  const approve = async (id) => {
    setBusyId(id)
    try { await HostelApi.approve(id); toast.success('Application approved'); reload() }
    catch (e) { toast.error(apiMessage(e)) } finally { setBusyId(null) }
  }
  const submitReject = async () => {
    if (!rejectReason.trim()) return toast.error('Please provide a reason for rejection')
    setBusyId(rejectTarget)
    try {
      await HostelApi.reject(rejectTarget, rejectReason.trim())
      toast.success('Application rejected'); setRejectTarget(null); setRejectReason(''); reload()
    } catch (e) { toast.error(apiMessage(e)) } finally { setBusyId(null) }
  }

  if (!canApply && !canManage && !canPay) {
    return <Card className="p-4"><EmptyState icon={BedDouble} title="No actions available" hint="Your role can't manage hostel applications." /></Card>
  }

  return (
    <div className="space-y-6">
      <div className={`grid gap-6 ${(canApply && canPay) ? 'lg:grid-cols-2' : 'lg:grid-cols-1'}`}>
        {canApply && (
          <Card className="p-6">
            <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>Apply for hostel</h3>
            <div className="space-y-4">
              <Field label="Student ID"><Input type="number" value={apply.studentId} onChange={(e) => setApply({ ...apply, studentId: e.target.value })} placeholder="5" /></Field>
              <Field label="Room type"><Select options={ROOM_TYPES} value={apply.roomType} onChange={(e) => setApply({ ...apply, roomType: e.target.value })} /></Field>
              <Field label="Reason"><Input value={apply.reason} onChange={(e) => setApply({ ...apply, reason: e.target.value })} placeholder="Home is too far from campus" /></Field>
              <Button onClick={doApply} loading={applying}><Plus size={16} /> Submit application</Button>
            </div>
          </Card>
        )}
        {canPay && (
          <Card className="p-6">
            <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>Pay hostel fee</h3>
            <Field label="Application ID"><Input type="number" value={appId} onChange={(e) => setAppId(e.target.value)} placeholder="1" /></Field>
            <Button className="mt-4" onClick={() => { if (!appId) return toast.error('Enter an application ID'); setPayOpen(true) }}><IndianRupee size={15} /> Make payment</Button>
          </Card>
        )}
      </div>

      {canManage && (
        <Card className="p-4">
          <h3 className="font-display font-semibold mb-4 px-1" style={{ color: 'var(--text)' }}>All hostel applications</h3>
          {loading ? <Spinner /> : applications.length === 0 ? <EmptyState icon={BedDouble} title="No applications yet" />
            : <Table head={['ID', 'Student', 'Room type', 'Reason', 'Fee', 'Payment', 'Status', '']}>
                {applications.map((a) => (
                  <Row key={a.applicationId}>
                    <Cell mono>{a.applicationId}</Cell>
                    <Cell>{a.studentName || a.studentId}</Cell>
                    <Cell><Badge value={a.roomType} /></Cell>
                    <Cell className="max-w-[200px] truncate">{a.reason}</Cell>
                    <Cell>{a.hostelFee ? `₹${Number(a.hostelFee).toLocaleString('en-IN')}` : '—'}</Cell>
                    <Cell><Badge value={a.paymentStatus} /></Cell>
                    <Cell>
                      <Badge value={a.status} />
                      {a.status === 'REJECTED' && a.rejectionReason && (
                        <div className="text-xs mt-1" style={{ color: 'var(--text-faint)' }}>{a.rejectionReason}</div>
                      )}
                    </Cell>
                    <Cell>
                      {a.status === 'PENDING' && (
                        <div className="flex gap-1">
                          <button onClick={() => approve(a.applicationId)} disabled={busyId === a.applicationId} title="Approve" className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-500 disabled:opacity-50"><Check size={15} /></button>
                          <button onClick={() => { setRejectTarget(a.applicationId); setRejectReason('') }} disabled={busyId === a.applicationId} title="Reject" className="p-1.5 rounded-lg hover:bg-rose-500/10 text-rose-500 disabled:opacity-50"><X size={15} /></button>
                        </div>
                      )}
                    </Cell>
                  </Row>
                ))}
              </Table>}
        </Card>
      )}

      <HostelPaymentModal
        open={payOpen}
        onClose={() => setPayOpen(false)}
        toast={toast}
        appId={appId}
        onDone={() => { setPayOpen(false); reload() }}
      />

      <Modal open={!!rejectTarget} onClose={() => setRejectTarget(null)} title="Reject application">
        <div className="space-y-4">
          <Field label="Reason for rejection" hint="This will be shown to the student.">
            <Input value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} placeholder="e.g. Incomplete supporting documents" />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setRejectTarget(null)}>Cancel</Button>
            <Button variant="danger" onClick={submitReject} loading={busyId === rejectTarget}>Reject</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

// Payment modes for the hostel module (mirrors the fees module).
const HOSTEL_PAY_MODES = [
  { value: 'UPI', label: 'UPI' },
  { value: 'ONLINE', label: 'Online (Card)' },
  { value: 'NETBANKING', label: 'Net banking' },
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'DD', label: 'Demand Draft (DD)' },
]

function HostelPaymentModal({ open, onClose, toast, appId, onDone }) {
  const [mode, setMode] = useState('UPI')
  const [step, setStep] = useState('form')
  const [referenceNo, setReferenceNo] = useState('')
  const [saving, setSaving] = useState(false)
  const [card, setCard] = useState({ name: '', number: '', amount: '', expiry: '', cvv: '' })
  const [notes, setNotes] = useState({ n500: 0, n100: 0, other: [{ amount: '', count: '' }] })
  const [primed, setPrimed] = useState(false)

  if (open && !primed) {
    setPrimed(true); setMode('UPI'); setStep('form'); setReferenceNo('')
    setCard({ name: '', number: '', amount: '', expiry: '', cvv: '' })
    setNotes({ n500: 0, n100: 0, other: [{ amount: '', count: '' }] })
  }
  if (!open && primed) setPrimed(false)

  const cashTotal = () => {
    const other = notes.other.reduce((s, o) => s + (Number(o.amount) || 0) * (Number(o.count) || 0), 0)
    return notes.n500 * 500 + notes.n100 * 100 + other
  }

  const pay = async (finalMode) => {
    setSaving(true)
    try {
      await HostelApi.pay(Number(appId), { paymentMode: finalMode })
      toast.success('Hostel fee paid'); onDone()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  const proceed = () => {
    if (mode === 'CASH') return pay('CASH')
    if (mode === 'UPI') return setStep('upi')
    if (mode === 'ONLINE' || mode === 'NETBANKING') return setStep('card')
    if (mode === 'DD' || mode === 'BANK_TRANSFER') return setStep('proof')
  }

  const payWithCard = () => {
    if (!card.name || !card.number || !card.amount || !card.expiry || !card.cvv) return toast.error('Fill in all card details')
    pay(mode)
  }

  return (
    <Modal open={open} onClose={onClose} title="Make hostel payment" size={step === 'card' ? 'lg' : 'md'}>
      {step === 'form' && (
        <div className="space-y-4">
          <Field label="Payment mode"><Select options={HOSTEL_PAY_MODES} value={mode} onChange={(e) => setMode(e.target.value)} /></Field>

          {mode === 'CASH' && (
            <div className="rounded-xl p-3 space-y-3 border" style={{ borderColor: 'var(--border)' }}>
              <p className="text-xs font-semibold" style={{ color: 'var(--text-faint)' }}>Cash denomination breakdown</p>
              <div className="grid grid-cols-2 gap-3">
                <Field label="No. of ₹500 notes"><Input type="number" min="0" value={notes.n500} onChange={(e) => setNotes({ ...notes, n500: Number(e.target.value) || 0 })} /></Field>
                <Field label="No. of ₹100 notes"><Input type="number" min="0" value={notes.n100} onChange={(e) => setNotes({ ...notes, n100: Number(e.target.value) || 0 })} /></Field>
              </div>
              {notes.other.map((o, i) => (
                <div key={i} className="grid grid-cols-2 gap-3">
                  <Field label="Other denomination (₹)"><Input type="number" min="0" value={o.amount} onChange={(e) => setNotes({ ...notes, other: notes.other.map((x, idx) => idx === i ? { ...x, amount: e.target.value } : x) })} placeholder="200" /></Field>
                  <Field label="No. of notes"><Input type="number" min="0" value={o.count} onChange={(e) => setNotes({ ...notes, other: notes.other.map((x, idx) => idx === i ? { ...x, count: e.target.value } : x) })} placeholder="0" /></Field>
                </div>
              ))}
              <Button variant="subtle" size="sm" onClick={() => setNotes({ ...notes, other: [...notes.other, { amount: '', count: '' }] })}><Plus size={13} /> Add another denomination</Button>
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>Counted total: <span className="font-semibold">₹{cashTotal().toLocaleString('en-IN')}</span></p>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={onClose}>Cancel</Button>
            <Button onClick={proceed} loading={saving && mode === 'CASH'}>{mode === 'CASH' ? 'Record payment' : 'Continue'}</Button>
          </div>
        </div>
      )}

      {step === 'upi' && (
        <div className="space-y-4 text-center">
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Scan and pay the hostel fee using any UPI app, then confirm below.</p>
          <img src="/hostel-payment-qr.png" alt="UPI QR for hostel payment" className="mx-auto rounded-xl border w-56 h-56 object-contain" style={{ borderColor: 'var(--border)' }} />
          <Field label="UTR / transaction reference"><Input value={referenceNo} onChange={(e) => setReferenceNo(e.target.value)} placeholder="e.g. 402913579246" /></Field>
          <div className="flex justify-center gap-2 pt-2">
            <Button variant="outline" onClick={() => setStep('form')}>Back</Button>
            <Button onClick={() => { if (!referenceNo) return toast.error('Enter the UPI reference number'); pay('UPI') }} loading={saving}><ShieldCheck size={15} /> I've paid — confirm</Button>
          </div>
        </div>
      )}

      {step === 'card' && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 text-sm" style={{ color: 'var(--text-muted)' }}>
            <CreditCard size={16} /> Enter card / net banking details to pay the hostel fee.
          </div>
          <Field label="Name on card"><Input value={card.name} onChange={(e) => setCard({ ...card, name: e.target.value })} placeholder="Card holder name" /></Field>
          <Field label="Card number"><Input value={card.number} onChange={(e) => setCard({ ...card, number: e.target.value })} placeholder="4242 4242 4242 4242" maxLength={19} /></Field>
          <div className="grid grid-cols-3 gap-3">
            <Field label="Amount"><Input type="number" value={card.amount} onChange={(e) => setCard({ ...card, amount: e.target.value })} placeholder="Amount" /></Field>
            <Field label="Expiry"><Input value={card.expiry} onChange={(e) => setCard({ ...card, expiry: e.target.value })} placeholder="MM/YY" /></Field>
            <Field label="CVV"><Input value={card.cvv} onChange={(e) => setCard({ ...card, cvv: e.target.value })} placeholder="123" maxLength={4} /></Field>
          </div>
          <p className="text-xs" style={{ color: 'var(--text-faint)' }}>This is a simulated checkout for demo purposes — no real card network is contacted.</p>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setStep('form')}>Back</Button>
            <Button onClick={payWithCard} loading={saving}><CreditCard size={15} /> Pay now</Button>
          </div>
        </div>
      )}

      {step === 'proof' && (
        <div className="space-y-4">
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            For {mode === 'DD' ? 'demand draft' : 'bank transfer'} payments, enter your reference and confirm. The hostel office will verify the receipt.
          </p>
          <Field label={mode === 'DD' ? 'DD number' : 'Transaction / UTR reference'}>
            <Input value={referenceNo} onChange={(e) => setReferenceNo(e.target.value)} placeholder="Reference number" />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setStep('form')}>Back</Button>
            <Button onClick={() => { if (!referenceNo) return toast.error('Enter a reference number'); pay(mode) }} loading={saving}>Submit payment</Button>
          </div>
        </div>
      )}
    </Modal>
  )
}

function Allotments({ toast, role }) {
  const canManage = can(role, 'hostel.allot')
  const [form, setForm] = useState({ studentId: '', roomId: '', academicYear: '2026-27', checkinDate: '' })
  const [saving, setSaving] = useState(false)
  const [studentId, setStudentId] = useState('')
  const [searchData, setSearchData] = useState(null)
  const [searching, setSearching] = useState(false)

  const { data: allData, loading: allLoading, reload: reloadAll } = useAsync(
    () => (canManage ? HostelApi.allAllotments() : Promise.resolve([])), [canManage]
  )
  const allAllotments = asArray(allData)

  const allot = async () => {
    setSaving(true)
    try {
      await HostelApi.allot({ studentId: Number(form.studentId), roomId: Number(form.roomId), academicYear: form.academicYear, checkinDate: form.checkinDate })
      window.dispatchEvent(new Event('cc:data-changed')); toast.success('Room allotted'); setForm({ studentId: '', roomId: '', academicYear: '2026-27', checkinDate: '' })
      canManage ? reloadAll() : null
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }
  const searchByStudent = async () => {
    setSearching(true)
    try { setSearchData(asArray(await HostelApi.studentAllotments(Number(studentId)))) }
    catch (e) { toast.error(apiMessage(e)) } finally { setSearching(false) }
  }
  const vacate = async (id) => {
    try { await HostelApi.vacate(id); window.dispatchEvent(new Event('cc:data-changed')); toast.success('Room vacated'); canManage ? reloadAll() : searchByStudent() }
    catch (e) { toast.error(apiMessage(e)) }
  }

  const renderRows = (rows) => rows.map((a) => (
    <Row key={a.allotmentId}>
      <Cell mono>{a.allotmentId}</Cell>
      {canManage && <Cell>{a.studentName || a.studentId}</Cell>}
      <Cell>{a.hostelBlock ? `${a.hostelBlock} · ${a.roomNumber}` : (a.roomNumber || a.roomId)}</Cell>
      <Cell>{a.academicYear}</Cell>
      <Cell>{a.checkinDate}</Cell>
      <Cell><Badge value={a.status} /></Cell>
      {canManage && <Cell>{String(a.status).toUpperCase() === 'ACTIVE' && <button onClick={() => vacate(a.allotmentId)} title="Vacate" className="p-1.5 rounded-lg hover:bg-rose-500/10 text-rose-500"><LogOut size={15} /></button>}</Cell>}
    </Row>
  ))

  return (
    <div className="space-y-6">
      <div className={`grid gap-6 ${canManage ? 'lg:grid-cols-2' : 'lg:grid-cols-1'}`}>
        {canManage && (
          <Card className="p-6">
            <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>Allot room</h3>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <Field label="Student ID"><Input type="number" value={form.studentId} onChange={(e) => setForm({ ...form, studentId: e.target.value })} placeholder="5" /></Field>
                <Field label="Room ID"><Input type="number" value={form.roomId} onChange={(e) => setForm({ ...form, roomId: e.target.value })} placeholder="1" /></Field>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <Field label="Academic year"><Input value={form.academicYear} onChange={(e) => setForm({ ...form, academicYear: e.target.value })} /></Field>
                <Field label="Check-in date"><Input type="date" value={form.checkinDate} onChange={(e) => setForm({ ...form, checkinDate: e.target.value })} /></Field>
              </div>
              <Button onClick={allot} loading={saving}><Plus size={16} /> Allot room</Button>
            </div>
          </Card>
        )}
        {!canManage && (
          <Card className="p-6">
            <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>My allotments</h3>
            <div className="flex items-end gap-3 mb-4">
              <div className="flex-1"><span className="label">Student ID</span><Input type="number" value={studentId} onChange={(e) => setStudentId(e.target.value)} placeholder="5" /></div>
              <Button onClick={searchByStudent} loading={searching}><Search size={16} /> Load</Button>
            </div>
            {searching ? <Spinner /> : !searchData ? <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Enter a student ID to see their allotment history.</p>
              : searchData.length === 0 ? <EmptyState icon={BedDouble} title="No allotments" />
              : <Table head={['ID', 'Room', 'Year', 'Check-in', 'Status']}>{renderRows(searchData)}</Table>}
          </Card>
        )}
      </div>

      {canManage && (
        <Card className="p-4">
          <h3 className="font-display font-semibold mb-4 px-1" style={{ color: 'var(--text)' }}>All hostel allotments</h3>
          {allLoading ? <Spinner /> : allAllotments.length === 0 ? <EmptyState icon={BedDouble} title="No allotments yet" hint="Allotted rooms will show up here automatically." />
            : <Table head={['ID', 'Student', 'Room', 'Year', 'Check-in', 'Status', '']}>{renderRows(allAllotments)}</Table>}
        </Card>
      )}
    </div>
  )
}
