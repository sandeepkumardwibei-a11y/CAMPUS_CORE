import { useState, useEffect } from 'react'
import { Wallet, Search, Receipt, Plus, IndianRupee, CreditCard, Upload, ShieldCheck, X as XIcon, Eye, AlertCircle, Clock } from 'lucide-react'
import { FeeApi } from '../lib/services'
import api from '../lib/api'
import { asArray, useAsync } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { INVOICE_STATUS, can, currentAcademicYear, academicYearError } from '../lib/constants'
import {
  PageHeader, Card, Button, Table, Row, Cell, Badge, Spinner, EmptyState,
  Modal, Field, Input, Select,
} from '../components/ui'
import { Tabs } from '../components/ui/extras'
import { StudentSelect } from '../components/ui/StudentSelect'

const money = (n) => (n == null ? '—' : `₹${Number(n).toLocaleString('en-IN')}`)
const invEmpty = { studentId: '', academicYear: currentAcademicYear(), semester: 3, tuitionFee: 0, libraryFee: 0, labFee: 0, activityFee: 0, scholarshipAdjusted: 0, dueDate: '' }

// Payment modes available in the fee module (student self-pay and staff recording).
const STUDENT_PAY_MODES = [
  { value: 'UPI', label: 'UPI' },
  { value: 'ONLINE', label: 'Online (Card)' },
  { value: 'NETBANKING', label: 'Net banking' },
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'DD', label: 'Demand Draft (DD)' },
]
const ADMIN_PAY_MODES = [
  { value: 'UPI', label: 'UPI' },
  { value: 'ONLINE', label: 'Online (Card)' },
  { value: 'NETBANKING', label: 'Net banking' },
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'DD', label: 'Demand Draft (DD)' },
]

export default function Fees() {
  const toast = useToast()
  const { user } = useAuth()
  const canManage = can(user?.role, 'fee.createInvoice')
  const canPay = can(user?.role, 'fee.recordPayment')
  const canStatus = can(user?.role, 'fee.invoicesByStatus')
  const isStudent = user?.role === 'STUDENT'
  const [tab, setTab] = useState(canStatus ? 'status' : 'student')
  const [rows, setRows] = useState(null)
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState('GENERATED')
  const [studentId, setStudentId] = useState(canStatus ? '' : (user?.userId || ''))
  const [invModal, setInvModal] = useState(false)
  const [payModal, setPayModal] = useState(false)
  const [payingInvoice, setPayingInvoice] = useState(null)
  const [inv, setInv] = useState(invEmpty)
  const [saving, setSaving] = useState(false)
  const [payments, setPayments] = useState(null)

  // Dashboard counts (ADMIN / ACCOUNTS): how many invoices are unpaid vs partially paid.
  const [counts, setCounts] = useState({ generated: null, partial: null })
  const loadCounts = async () => {
    try {
      // The status endpoint returns a Spring Page — read totalElements for the count.
      const [gen, par] = await Promise.all([
        FeeApi.invoicesByStatus({ status: 'GENERATED', page: 0, size: 1 }).catch(() => null),
        FeeApi.invoicesByStatus({ status: 'PARTIALLY_PAID', page: 0, size: 1 }).catch(() => null),
      ])
      const total = (r) => (r && typeof r === 'object' && 'totalElements' in r ? r.totalElements : asArray(r).length)
      setCounts({ generated: total(gen), partial: total(par) })
    } catch { /* non-fatal */ }
  }

  // A summary card click jumps to the By-status tab filtered on that status.
  const openStatus = (targetStatus) => {
    setTab('status')
    setStatus(targetStatus)
    setLoading(true)
    FeeApi.invoicesByStatus({ status: targetStatus, page: 0, size: 50 })
      .then((r) => setRows(asArray(r)))
      .catch((e) => toast.error(apiMessage(e)))
      .finally(() => setLoading(false))
  }

  const loadStatus = async () => {
    setLoading(true)
    try { setRows(asArray(await FeeApi.invoicesByStatus({ status, page: 0, size: 50 }))) }
    catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }
  const loadStudent = async () => {
    setLoading(true)
    try { setRows(asArray(await FeeApi.studentInvoices(Number(studentId)))) }
    catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }
  const viewPayments = async (invoiceId) => {
    try { setPayments({ invoiceId, list: asArray(await FeeApi.invoicePayments(invoiceId)) }) }
    catch (e) { toast.error(apiMessage(e)) }
  }
  const createInvoice = async () => {
    const ayErr = academicYearError(inv.academicYear)
    if (ayErr) return toast.error(ayErr)
    setSaving(true)
    try {
      const num = (v) => Number(v) || 0
      await FeeApi.createInvoice({
        studentId: Number(inv.studentId), academicYear: inv.academicYear, semester: Number(inv.semester),
        tuitionFee: num(inv.tuitionFee), libraryFee: num(inv.libraryFee),
        labFee: num(inv.labFee), activityFee: num(inv.activityFee), scholarshipAdjusted: num(inv.scholarshipAdjusted),
        dueDate: inv.dueDate,
      })
      toast.success('Invoice generated'); setInvModal(false); setInv(invEmpty)
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }
  const afterPaymentAction = () => {
    setPayModal(false); setPayingInvoice(null)
    if (tab === 'status') loadStatus(); else if (studentId) loadStudent()
    if (canStatus) loadCounts()
  }

  // Load the dashboard counts once for ADMIN / ACCOUNTS.
  useEffect(() => {
    if (canStatus) loadCounts()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canStatus])

  return (
    <div>
      <PageHeader icon={Wallet} title="Fees"
        subtitle={canManage ? 'Generate invoices, record payments and audit dues.' : 'View your invoices and payment history.'}
        actions={(canPay || canManage) && <>
          {canPay && <Button variant="outline" onClick={() => { setPayingInvoice(null); setPayModal(true) }}><IndianRupee size={16} /> Record payment</Button>}
          {canManage && <Button onClick={() => setInvModal(true)}><Plus size={16} /> Generate invoice</Button>}
        </>} />

      {canStatus && (
        <div className="grid sm:grid-cols-2 gap-4 mb-6">
          <button onClick={() => openStatus('GENERATED')} className="text-left">
            <Card className="p-5 flex items-center gap-4 hover:ring-2 hover:ring-rose-500/40 transition cursor-pointer">
              <div className="w-12 h-12 rounded-xl grid place-items-center bg-rose-500/12 text-rose-500 shrink-0"><AlertCircle size={22} /></div>
              <div>
                <p className="text-2xl font-bold" style={{ color: 'var(--text)' }}>{counts.generated ?? '—'}</p>
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Students not paid (invoice generated)</p>
                <p className="text-xs mt-0.5 text-rose-500">Click to view →</p>
              </div>
            </Card>
          </button>
          <button onClick={() => openStatus('PARTIALLY_PAID')} className="text-left">
            <Card className="p-5 flex items-center gap-4 hover:ring-2 hover:ring-amber-500/40 transition cursor-pointer">
              <div className="w-12 h-12 rounded-xl grid place-items-center bg-amber-500/12 text-amber-500 shrink-0"><Clock size={22} /></div>
              <div>
                <p className="text-2xl font-bold" style={{ color: 'var(--text)' }}>{counts.partial ?? '—'}</p>
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Students partially paid</p>
                <p className="text-xs mt-0.5 text-amber-500">Click to view →</p>
              </div>
            </Card>
          </button>
        </div>
      )}

      <Tabs active={tab} onChange={(t) => { setTab(t); setRows(null) }} tabs={[
        ...(canStatus ? [{ key: 'status', label: 'By status' }] : []),
        { key: 'student', label: 'By student' },
        ...(canManage ? [{ key: 'pending', label: 'Pending verifications' }] : []),
      ]} />

      {tab === 'pending' && canManage ? (
        <PendingVerifications toast={toast} />
      ) : (
        <>
          <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
            {tab === 'status' ? <>
              <div className="w-44"><span className="label">Invoice status</span><Select options={INVOICE_STATUS} value={status} onChange={(e) => setStatus(e.target.value)} /></div>
              <Button onClick={loadStatus} loading={loading}><Search size={16} /> Load invoices</Button>
            </> : <>
              {canStatus
                ? <div className="w-64"><span className="label">Student</span><StudentSelect allStudents value={studentId} onChange={(id) => setStudentId(id ?? '')} /></div>
                : isStudent
                  ? <div className="w-64"><span className="label">Student</span><div className="field flex items-center" style={{ color: 'var(--text)' }}>{user?.name || 'You'}</div></div>
                  : <div className="w-40"><span className="label">Student ID</span><Input type="number" min={1} max={999999} value={studentId} onChange={(e) => setStudentId(e.target.value)} placeholder="5" /></div>}
              <Button onClick={loadStudent} loading={loading}><Search size={16} /> Load invoices</Button>
            </>}
          </Card>

          <Card className="p-4">
            {loading ? <Spinner /> : !rows ? <EmptyState icon={Wallet} title="Load invoices" hint="Filter by status or student to view invoices." />
              : rows.length === 0 ? <EmptyState icon={Wallet} title="No invoices found" />
              : <Table head={['ID', 'Student', 'Year', 'Sem', 'Total payable', 'Scholarship', 'Net payable', 'Due date', 'Status', '']}>
                  {rows.map((r) => (
                    <Row key={r.invoiceId}>
                      <Cell mono>{r.invoiceId}</Cell>
                      <Cell>{r.studentName || r.studentId}</Cell>
                      <Cell>{r.academicYear}</Cell>
                      <Cell>{r.semester}</Cell>
                      <Cell>{money(r.totalAmount)}</Cell>
                      <Cell>{r.scholarshipAdjusted ? <span className="text-emerald-500">-{money(r.scholarshipAdjusted)}</span> : money(0)}</Cell>
                      <Cell className="font-semibold">{money(r.netPayable)}</Cell>
                      <Cell>{r.dueDate}</Cell>
                      <Cell><Badge value={r.status} /></Cell>
                      <Cell>
                        <div className="flex gap-1">
                          <button onClick={() => viewPayments(r.invoiceId)} className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-400" title="View payments"><Receipt size={15} /></button>
                          {isStudent && r.status !== 'PAID' && (
                            <button onClick={() => { setPayingInvoice(r); setPayModal(true) }} className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-500" title="Make payment"><IndianRupee size={15} /></button>
                          )}
                        </div>
                      </Cell>
                    </Row>
                  ))}
                </Table>}
          </Card>
        </>
      )}

      {/* Generate invoice */}
      <Modal open={invModal} onClose={() => setInvModal(false)} title="Generate invoice" size="lg">
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <Field label="Student"><StudentSelect allStudents value={inv.studentId} onChange={(id) => setInv({ ...inv, studentId: id ?? '' })} /></Field>
            <Field label="Academic year"><Input value={inv.academicYear} onChange={(e) => setInv({ ...inv, academicYear: e.target.value })} /></Field>
            <Field label="Semester"><Input type="number" min={1} max={8} value={inv.semester} onChange={(e) => setInv({ ...inv, semester: e.target.value })} /></Field>
          </div>
          <div className="grid grid-cols-3 gap-4">
            {['tuitionFee', 'libraryFee', 'labFee', 'activityFee', 'scholarshipAdjusted'].map((k) => (
              <Field key={k} label={k.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())}>
                <Input type="number" value={inv[k]} onChange={(e) => setInv({ ...inv, [k]: e.target.value })} />
              </Field>
            ))}
          </div>
          <Field label="Due date"><Input type="date" value={inv.dueDate} onChange={(e) => setInv({ ...inv, dueDate: e.target.value })} /></Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setInvModal(false)}>Cancel</Button>
            <Button onClick={createInvoice} loading={saving}>Generate</Button>
          </div>
        </div>
      </Modal>

      <PaymentModal
        open={payModal}
        onClose={() => { setPayModal(false); setPayingInvoice(null) }}
        toast={toast}
        isStudent={isStudent}
        invoice={payingInvoice}
        onDone={afterPaymentAction}
      />

      {/* Payments viewer */}
      <Modal open={!!payments} onClose={() => setPayments(null)} title={`Payments · Invoice #${payments?.invoiceId}`} size="lg">
        {!payments?.list?.length ? <EmptyState icon={Receipt} title="No payments recorded" />
          : <Table head={['ID', 'Amount', 'Mode', 'Reference', 'Receipt', 'Status', '']}>
              {payments.list.map((p) => (
                <Row key={p.paymentId}>
                  <Cell mono>{p.paymentId}</Cell>
                  <Cell>{money(p.paidAmount)}</Cell>
                  <Cell><Badge value={p.mode} /></Cell>
                  <Cell mono>{p.referenceNo || '—'}</Cell>
                  <Cell mono>{p.receiptNumber || '—'}</Cell>
                  <Cell>
                    <Badge value={p.status} />
                    {p.status === 'REJECTED' && p.verificationReason && (
                      <div className="text-xs mt-1" style={{ color: 'var(--text-faint)' }}>{p.verificationReason}</div>
                    )}
                  </Cell>
                  <Cell>{p.hasProof && <ProofViewButton paymentId={p.paymentId} />}</Cell>
                </Row>
              ))}
            </Table>}
      </Modal>
    </div>
  )
}

function PaymentModal({ open, onClose, toast, isStudent, invoice, onDone }) {
  const [invoiceId, setInvoiceId] = useState('')
  const [amount, setAmount] = useState('')
  const [mode, setMode] = useState(isStudent ? 'UPI' : 'CASH')
  const [referenceNo, setReferenceNo] = useState('')
  const [saving, setSaving] = useState(false)
  const [step, setStep] = useState('form')
  const [card, setCard] = useState({ name: '', number: '', expiry: '', cvv: '' })
  const [proofFile, setProofFile] = useState(null)
  const [notes, setNotes] = useState({ n500: 0, n100: 0, other: [{ amount: '', count: '' }] })
  const [primed, setPrimed] = useState(false)

  const modeOptions = isStudent ? STUDENT_PAY_MODES : ADMIN_PAY_MODES

  if (open && !primed) {
    setPrimed(true)
    setInvoiceId(invoice?.invoiceId ? String(invoice.invoiceId) : '')
    setAmount(invoice?.netPayable != null ? String(invoice.netPayable) : '')
    setMode(isStudent ? 'UPI' : 'CASH')
    setReferenceNo(''); setStep('form'); setCard({ name: '', number: '', expiry: '', cvv: '' })
    setProofFile(null); setNotes({ n500: 0, n100: 0, other: [{ amount: '', count: '' }] })
  }
  if (!open && primed) setPrimed(false)

  const cashTotal = () => {
    const other = notes.other.reduce((s, o) => s + (Number(o.amount) || 0) * (Number(o.count) || 0), 0)
    return notes.n500 * 500 + notes.n100 * 100 + other
  }
  const cashBreakdownNote = () => {
    const parts = []
    if (notes.n500 > 0) parts.push(`₹500 x ${notes.n500}`)
    if (notes.n100 > 0) parts.push(`₹100 x ${notes.n100}`)
    notes.other.forEach((o) => { if (Number(o.amount) > 0 && Number(o.count) > 0) parts.push(`₹${o.amount} x ${o.count}`) })
    return parts.join(', ')
  }

  const submitDirect = async (finalMode, finalReference) => {
    setSaving(true)
    try {
      await FeeApi.recordPayment({
        invoiceId: Number(invoiceId), paidAmount: Number(amount), mode: finalMode,
        referenceNo: finalReference || undefined,
        cashBreakdownNote: finalMode === 'CASH' ? cashBreakdownNote() || undefined : undefined,
      })
      toast.success('Payment recorded'); onDone()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  const submitProof = async () => {
    if (!proofFile) return toast.error('Please attach a photo of the payment proof')
    setSaving(true)
    try {
      const fd = new FormData()
      fd.append('invoiceId', invoiceId)
      fd.append('paidAmount', amount)
      fd.append('mode', mode)
      if (referenceNo) fd.append('referenceNo', referenceNo)
      fd.append('file', proofFile)
      await FeeApi.submitProof(fd)
      toast.success("Proof submitted — Accounts will verify and record your payment.")
      onDone()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSaving(false) }
  }

  const proceed = () => {
    if (!invoiceId || !amount) return toast.error('Enter invoice ID and amount')
    if (mode === 'CASH') {
      if (cashTotal() > 0 && Number(cashTotal()) !== Number(amount)) {
        return toast.error(`Cash counted (${money(cashTotal())}) does not match amount (${money(amount)})`)
      }
      return submitDirect('CASH', referenceNo)
    }
    if (mode === 'UPI') return setStep('upi')
    if (mode === 'ONLINE' || mode === 'NETBANKING') return setStep('card')
    if (mode === 'DD' || mode === 'BANK_TRANSFER') return setStep('proof')
  }

  const payWithCard = async () => {
    if (!card.name || !card.number || !card.expiry || !card.cvv) return toast.error('Fill in all card details')
    setSaving(true)
    setTimeout(() => {
      const fakeRef = `${mode}-${Date.now().toString().slice(-8)}`
      submitDirect(mode, fakeRef)
    }, 700)
  }

  return (
    <Modal open={open} onClose={onClose} title={isStudent ? 'Make payment' : 'Record payment'} size={step === 'card' ? 'lg' : 'md'}>
      {step === 'form' && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Invoice ID">
              <Input type="number" min={1} max={999999} value={invoiceId} onChange={(e) => setInvoiceId(e.target.value)} placeholder="1" disabled={!!invoice} />
            </Field>
            <Field label="Amount"><Input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="71000" /></Field>
          </div>
          <Field label="Mode"><Select options={modeOptions} value={mode} onChange={(e) => setMode(e.target.value)} /></Field>

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
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>Counted total: <span className="font-semibold">{money(cashTotal())}</span></p>
            </div>
          )}

          {mode !== 'CASH' && (
            <Field label="Reference no. (optional)"><Input value={referenceNo} onChange={(e) => setReferenceNo(e.target.value)} placeholder="TXN-98765" /></Field>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={onClose}>Cancel</Button>
            <Button onClick={proceed} loading={saving && mode === 'CASH'}>{mode === 'CASH' ? 'Record' : 'Continue'}</Button>
          </div>
        </div>
      )}

      {step === 'upi' && (
        <div className="space-y-4 text-center">
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Scan and pay {money(amount)} using any UPI app, then enter the UTR / transaction reference below.</p>
          <img src="/fee-payment-qr.png" alt="UPI QR for fee payment" className="mx-auto rounded-xl border w-56 h-56 object-contain" style={{ borderColor: 'var(--border)' }} />
          <Field label="UTR / transaction reference"><Input value={referenceNo} onChange={(e) => setReferenceNo(e.target.value)} placeholder="e.g. 402913579246" /></Field>
          <div className="flex justify-center gap-2 pt-2">
            <Button variant="outline" onClick={() => setStep('form')}>Back</Button>
            <Button onClick={() => { if (!referenceNo) return toast.error('Enter the UPI reference number'); submitDirect('UPI', referenceNo) }} loading={saving}>
              <ShieldCheck size={15} /> I've paid — confirm
            </Button>
          </div>
        </div>
      )}

      {step === 'card' && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 text-sm" style={{ color: 'var(--text-muted)' }}>
            <CreditCard size={16} /> Enter card / net banking details to pay {money(amount)}.
          </div>
          <Field label="Name on card"><Input value={card.name} onChange={(e) => setCard({ ...card, name: e.target.value })} placeholder="Khushal Kumar" /></Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Card number"><Input value={card.number} onChange={(e) => setCard({ ...card, number: e.target.value })} placeholder="4242 4242 4242 4242" maxLength={19} /></Field>
            <div className="grid grid-cols-2 gap-2">
              <Field label="Expiry"><Input value={card.expiry} onChange={(e) => setCard({ ...card, expiry: e.target.value })} placeholder="MM/YY" /></Field>
              <Field label="CVV"><Input value={card.cvv} onChange={(e) => setCard({ ...card, cvv: e.target.value })} placeholder="123" maxLength={4} /></Field>
            </div>
          </div>
          <p className="text-xs" style={{ color: 'var(--text-faint)' }}>This is a simulated checkout for demo purposes — no real card network is contacted.</p>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setStep('form')}>Back</Button>
            <Button onClick={payWithCard} loading={saving}><CreditCard size={15} /> Pay {money(amount)}</Button>
          </div>
        </div>
      )}

      {step === 'proof' && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 text-sm" style={{ color: 'var(--text-muted)' }}>
            <Upload size={16} /> Upload a photo of your {mode === 'DD' ? 'demand draft' : 'bank transfer'} receipt.
          </div>
          <Field label={mode === 'DD' ? 'DD number' : 'Transaction / UTR reference'}>
            <Input value={referenceNo} onChange={(e) => setReferenceNo(e.target.value)} placeholder="Reference number" />
          </Field>
          <Field label="Proof photo">
            <input type="file" accept="image/*,.pdf" className="field" onChange={(e) => setProofFile(e.target.files?.[0] || null)} />
          </Field>
          <p className="text-xs" style={{ color: 'var(--text-faint)' }}>Your payment will show as pending until Accounts verifies the proof and records it.</p>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setStep('form')}>Back</Button>
            <Button onClick={submitProof} loading={saving}><Upload size={15} /> Submit for verification</Button>
          </div>
        </div>
      )}
    </Modal>
  )
}

function ProofViewButton({ paymentId }) {
  const [url, setUrl] = useState(null)
  const [open, setOpen] = useState(false)
  const view = async () => {
    try {
      const res = await api.get(`/fees/payments/${paymentId}/proof`, { responseType: 'blob' })
      setUrl(URL.createObjectURL(res.data)); setOpen(true)
    } catch (e) { /* ignore */ }
  }
return (
    <>
      <button onClick={view} className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-400" title="View proof">
        <Eye size={15} />
      </button>
      
      <Modal open={open} onClose={() => setOpen(false)} title="Payment proof" size= "">
        {url ? (
          <div className="w-full flex justify-center items-center overflow-hidden bg-slate-900/50 rounded-xl p-2 border border-white/5">
            <img 
              src={url} 
              alt="Payment proof" 
              className="w-full max-h-[170vh] object-contain rounded-lg shadow-md" 
            />
          </div>
        ) : (
          <p className="text-sm text-center py-6" style={{ color: 'var(--text-muted)' }}>
            No proof document payload available.
          </p>
        )}
      </Modal>
    </>
  )
}

function PendingVerifications({ toast }) {
  const { data, loading, reload } = useAsync(() => FeeApi.pendingProofPayments(), [])
  const rows = asArray(data)
  const [busyId, setBusyId] = useState(null)
  const [rejectTarget, setRejectTarget] = useState(null)
  const [reason, setReason] = useState('')

  const confirm = async (id) => {
    setBusyId(id)
    try { await FeeApi.confirmProofPayment(id); toast.success('Payment verified and recorded'); reload() }
    catch (e) { toast.error(apiMessage(e)) } finally { setBusyId(null) }
  }
  const submitReject = async () => {
    setBusyId(rejectTarget)
    try { await FeeApi.rejectProofPayment(rejectTarget, reason); toast.success('Payment proof rejected'); setRejectTarget(null); setReason(''); reload() }
    catch (e) { toast.error(apiMessage(e)) } finally { setBusyId(null) }
  }

  return (
    <Card className="p-4">
      {loading ? <Spinner /> : rows.length === 0 ? <EmptyState icon={ShieldCheck} title="Nothing awaiting verification" hint="Bank transfer / DD proofs submitted by students will show up here." />
        : <Table head={['ID', 'Student', 'Invoice', 'Amount', 'Mode', 'Reference', 'Proof', '']}>
            {rows.map((p) => (
              <Row key={p.paymentId}>
                <Cell mono>{p.paymentId}</Cell>
                <Cell>{p.studentName || p.studentId}</Cell>
                <Cell mono>{p.invoiceId}</Cell>
                <Cell>{money(p.paidAmount)}</Cell>
                <Cell><Badge value={p.mode} /></Cell>
                <Cell mono>{p.referenceNo || '—'}</Cell>
                <Cell>{p.hasProof && <ProofViewButton paymentId={p.paymentId} />}</Cell>
                <Cell>
                  <div className="flex gap-1">
                    <button onClick={() => confirm(p.paymentId)} disabled={busyId === p.paymentId} title="Confirm & record" className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-500 disabled:opacity-50"><ShieldCheck size={15} /></button>
                    <button onClick={() => { setRejectTarget(p.paymentId); setReason('') }} disabled={busyId === p.paymentId} title="Reject" className="p-1.5 rounded-lg hover:bg-rose-500/10 text-rose-500 disabled:opacity-50"><XIcon size={15} /></button>
                  </div>
                </Cell>
              </Row>
            ))}
          </Table>}
      <Modal open={!!rejectTarget} onClose={() => setRejectTarget(null)} title="Reject payment proof">
        <div className="space-y-4">
          <Field label="Reason"><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Proof image is unreadable" /></Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setRejectTarget(null)}>Cancel</Button>
            <Button variant="danger" onClick={submitReject} loading={busyId === rejectTarget}>Reject</Button>
          </div>
        </div>
      </Modal>
    </Card>
  )
}
