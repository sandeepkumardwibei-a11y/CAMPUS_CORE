import { useState, useCallback, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, RefreshCw, ThumbsUp, ThumbsDown, FileText, Check, X,
  FileCheck2, ScrollText, GraduationCap, Ban, Undo2, Eye, FileUp,
} from 'lucide-react'
import { AdmissionApi } from '../../lib/services'
import api from '../../lib/api'
import { apiMessage } from '../../lib/api'
import { useToast } from '../../context/ToastContext'
import {
  PageHeader, Card, Button, Field, Input, Badge, Spinner, Modal, EmptyState,
} from '../../components/ui'
import { Stepper } from '../../components/ui/extras'
import { ADMISSION_PIPELINE, can } from '../../lib/constants'
import { useAuth } from '../../context/AuthContext'

/**
 * Extract the application's status from whatever payload shape the backend returns.
 * The /status endpoint returns { applicationId, status }, while verification-details
 * returns the full entity. Both carry a usable status; this normalises either one.
 */
function extractCurrentStatus(payload) {
  if (!payload) return null

  if (typeof payload === 'string') {
    const s = payload.toUpperCase()
    return s === 'ISSUED' ? 'OFFER_ISSUED' : s
  }

  const nestedSource = payload.data || payload.application || payload

  const stringStatus =
    nestedSource.applicationStatus ||
    nestedSource.currentStatus ||
    nestedSource.admissionStatus ||
    (typeof nestedSource.status === 'string' ? nestedSource.status : null)

  if (stringStatus) {
    const resolved = String(stringStatus).toUpperCase()
    return resolved === 'ISSUED' ? 'OFFER_ISSUED' : resolved
  }

  // Fallback: scan the serialized payload for terminal markers.
  const serialized = JSON.stringify(payload).toUpperCase()
  if (serialized.includes('WITHDRAWN')) return 'WITHDRAWN'
  if (serialized.includes('REVOKED')) return 'REVOKED'
  if (serialized.includes('REJECTED')) return 'REJECTED'

  if (payload.status != null) return String(payload.status).toUpperCase()

  return null
}

export default function AdmissionDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()

  const { user } = useAuth()
  const role = user?.role
  const allow = (a) => can(role, a)

  const [data, setData] = useState(null)
  const [status, setStatus] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [modal, setModal] = useState(null)
  const [viewer, setViewer] = useState(null)
  const [revokeReason, setRevokeReason] = useState('')
  const [statusError, setStatusError] = useState(null)
  const [docSummary, setDocSummary] = useState(null)

  const appId = String(id || '').trim()

  /**
   * Load the application.
   *  1. Pipeline status comes from GET /admissions/{id}/status — the single source
   *     of truth for EVERY role (this is what the applicant view already used, and
   *     why it showed the correct status while the admin view defaulted to SUBMITTED).
   *  2. verification-details enriches the applicant meta card. It may legitimately
   *     fail (not-yet-verifiable app, or access denied for non-owners), so its
   *     failure must never clear the status we already resolved.
   */
  const load = useCallback(async () => {
    if (!user || !role) return
    setLoading(true)
    setStatusError(null)
    try {
      // 1) Authoritative status — works for admin, applicant, student, staff.
      let statusPayload = null
      try {
        statusPayload = await AdmissionApi.status(appId)
      } catch (e) {
        // Keep the real reason (e.g. access denied) so we never show a misleading status.
        setStatusError(apiMessage(e, 'Could not load application status'))
      }

      // 2a) Lightweight applicant meta — available at EVERY stage (incl. SUBMITTED).
      // This is what guarantees the admin sees the real applicant name, not their own.
      let basic = null
      try {
        const b = await AdmissionApi.basicInfo(appId)
        basic = b && b.success === false ? null : b
      } catch {
        /* ignore */
      }

      // 2b) Best-effort richer metadata (father/mother name, address…) — only after
      // OFFER_ACCEPTED, so it may legitimately be unavailable.
      let details = null
      try {
        const d = await AdmissionApi.verificationDetails(appId)
        details = d && d.success === false ? null : d
      } catch {
        /* ignore: status is still valid on its own */
      }

      // Merge for display, but keep the status payload authoritative for `status`.
      const merged = {
        ...(basic && typeof basic === 'object' ? basic : {}),
        ...(details && typeof details === 'object' ? details : {}),
        ...(statusPayload && typeof statusPayload === 'object' ? statusPayload : {}),
      }
      setData(Object.keys(merged).length ? merged : (statusPayload || details || basic))

      const resolved = extractCurrentStatus(statusPayload) || extractCurrentStatus(details)
      if (resolved) setStatus(resolved)

      // 3) Document upload summary — best effort, ignore if not permitted yet.
      try {
        setDocSummary(await AdmissionApi.documentSummary(appId))
      } catch {
        setDocSummary(null)
      }
    } finally {
      setLoading(false)
    }
  }, [appId, role, user, toast])

  useEffect(() => { load() }, [load])

  const run = async (fn, msg) => {
    setBusy(true)
    try {
      const response = await fn()
      toast.success(msg)
      const fresh = extractCurrentStatus(response)
      if (fresh) setStatus(fresh)
      await load()
      setModal(null)
    } catch (e) {
      toast.error(apiMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const view = async (fn, title) => {
    try {
      const d = await fn()
      setViewer({ title, data: d })
    } catch (e) {
      toast.error(apiMessage(e, `Cannot view ${title.toLowerCase()}`))
    }
  }

  const handleViewOfferLetter = async () => {
    try {
      const offerDetails = await AdmissionApi.offerDetails(appId)
      setViewer({ title: 'Offer details', data: offerDetails?.data || offerDetails })
    } catch (e) {
      // Graceful fallback: synthesise a readable summary from what we already have.
      if (data) {
        const src = data.data || data.application || data
        setViewer({
          title: 'Offer letter',
          data: {
            DocumentType: 'Official Admission Offer',
            ApplicationID: appId,
            RecipientName: src.applicantName || src.name || src.studentName || user?.name || '—',
            AssociatedEmail: src.email || user?.email || '—',
            TargetProgram: src.programName || src.program || '—',
            CurrentStatus: status || 'OFFER_ISSUED',
            Note: 'Issued by the CampusCore Admissions Panel.',
          },
        })
      } else {
        toast.error(apiMessage(e, 'No offer document available yet.'))
      }
    }
  }

  // Only trust a status we actually resolved from the backend — never guess SUBMITTED.
  const resolvedStatus = status || extractCurrentStatus(data)
  const statusKnown = !!resolvedStatus
  const currentStatusString = resolvedStatus ? String(resolvedStatus).toUpperCase() : null

  const isTerminal = ['REJECTED', 'WITHDRAWN', 'REVOKED', 'NOT_SHORTLISTED', 'OFFER_REJECTED', 'REJECTED_OFFER'].includes(currentStatusString)

  const metaSource = data?.data || data?.application || data || {}
  // Only show applicant meta if the application record itself carries it — never fall
  // back to the logged-in user's identity (that produced "System Admin" for the admin).
  const hasMetaAttributes =
    Object.keys(metaSource).length > 0 &&
    (metaSource.applicantName || metaSource.name || metaSource.email)

  const isOfferPendingAction = currentStatusString === 'OFFER_ISSUED'

  const hasVisibleActions =
    (allow('adm.evaluate') && !isTerminal) ||
    (allow('adm.issueOffer') && !isTerminal) ||
    (allow('adm.acceptOffer') && !isTerminal && isOfferPendingAction) ||
    (allow('adm.rejectOffer') && !isTerminal && isOfferPendingAction) ||
    allow('adm.view') ||
    (allow('adm.verifyDocuments') && !isTerminal) ||
    (allow('adm.issueAdmissionLetter') && !isTerminal) ||
    (allow('adm.finalizeEnrollment') && !isTerminal) ||
    (allow('adm.withdraw') && !isTerminal) ||
    (allow('adm.revokeOffer') && !isTerminal)

  if (!user || !role) {
    return <div className="p-12 flex justify-center items-center"><Spinner /></div>
  }

  return (
    <div>
      <button onClick={() => navigate('/admissions')} className="flex items-center gap-1.5 text-sm mb-4 hover:text-emerald-400 transition" style={{ color: 'var(--text-muted)' }}>
        <ArrowLeft size={15} /> Back to admissions
      </button>

      <PageHeader icon={GraduationCap} title={`Application #${appId}`}
        subtitle="Manage and view pipeline progression history records."
        actions={<Button variant="outline" onClick={load} loading={loading}><RefreshCw size={15} /> Refresh Data</Button>} />

      {loading ? <Spinner /> : (
        <>
          <Card className="p-6 mb-6">
            <div className="flex flex-wrap items-center gap-3 mb-5">
              <span className="text-sm font-semibold" style={{ color: 'var(--text-muted)' }}>Current Pipeline Status:</span>
              {statusKnown ? <Badge value={currentStatusString} /> : <Badge value="UNKNOWN" />}
            </div>

            {!statusKnown ? (
              <div className="mt-2 p-4 rounded-xl border border-amber-500/20 bg-amber-500/5">
                <p className="text-sm text-amber-500 font-medium">Status unavailable for this application.</p>
                {statusError && <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>{statusError}</p>}
              </div>
            ) : !isTerminal ? (
              <div className="mb-2">
                <Stepper steps={ADMISSION_PIPELINE} current={currentStatusString} />
              </div>
            ) : (
              <div className="mt-2 p-4 rounded-xl border border-rose-500/20 bg-rose-500/5 flex items-center gap-3">
                <div className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
                <p className="text-sm text-rose-400 font-medium">
                  This application journey is closed: <span className="underline font-bold tracking-wide">{currentStatusString.replace(/_/g, ' ')}</span>
                </p>
              </div>
            )}
          </Card>

          {data && typeof data === 'object' && hasMetaAttributes && (
            <Card className="p-6 mb-6">
              <h3 className="font-display font-semibold mb-3" style={{ color: 'var(--text)' }}>Applicant Meta Information</h3>
              <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 text-sm">
                {[
                  ['Name', metaSource.applicantName || metaSource.name || metaSource.studentName],
                  ['System Email', metaSource.email || metaSource.systemEmail],
                  ['Contact Phone', metaSource.phone || metaSource.contactPhone || data.phone],
                  ['Program Registration', metaSource.programName || metaSource.program || data.programName],
                  ['Department reference ID', metaSource.departmentId || data.departmentId],
                  ['Academic Term Year', metaSource.academicYear || data.academicYear],
                  ['Qualifying Grade Metric', metaSource.qualifyingScore ?? metaSource.percentageSecured ?? data.qualifyingScore],
                ].filter(([, v]) => v != null && v !== '').map(([k, v]) => (
                  <div key={k}>
                    <p className="text-xs mb-0.5" style={{ color: 'var(--text-faint)' }}>{k}</p>
                    <p className="font-medium" style={{ color: 'var(--text)' }}>{String(v)}</p>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {(role === 'APPLICANT' || role === 'STUDENT' || role === 'ADMIN') && !isTerminal && (
            <DocumentsCard
              appId={appId}
              role={role}
              summary={docSummary}
              onUploaded={load}
              isAdmin={role === 'ADMIN'}
            />
          )}

          {statusKnown && hasVisibleActions && (
            <>
              <h3 className="font-display text-lg font-semibold mb-4" style={{ color: 'var(--text)' }}>
                Application Workspaces
                <span className="ml-2 text-xs font-normal" style={{ color: 'var(--text-faint)' }}>· operational role permissions token ({String(role).toLowerCase()})</span>
              </h3>

              <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {allow('adm.evaluate') && !isTerminal && (
                  <ActionCard icon={ThumbsUp} title="Evaluate" desc="Shortlist or reject the incoming pipeline submission."
                    actions={<>
                      <Button size="sm" variant="subtle" onClick={() => run(() => AdmissionApi.evaluate(appId, true), 'Application Shortlisted')}><ThumbsUp size={14} /> Approve</Button>
                      <Button size="sm" variant="outline" onClick={() => run(() => AdmissionApi.evaluate(appId, false), 'Marked Not Shortlisted')}><ThumbsDown size={14} /> Reject</Button>
                    </>} />
                )}

                {allow('adm.issueOffer') && !isTerminal && (
                  <ActionCard icon={FileText} title="Issue Offer" desc="Send offer document payload along with a structural fee tracker code."
                    actions={<Button size="sm" onClick={() => setModal('offer')}><FileText size={14} /> Issue Offer</Button>} />
                )}

                {allow('adm.acceptOffer') && !isTerminal && isOfferPendingAction && (
                  <ActionCard icon={Check} title="Accept Offer" desc="Confirm acceptance and submit your enrolment details."
                    actions={<Button size="sm" variant="subtle" onClick={() => setModal('accept')}><Check size={14} /> Accept Offer</Button>} />
                )}

                {allow('adm.rejectOffer') && !isTerminal && isOfferPendingAction && (
                  <ActionCard icon={X} title="Reject Offer" desc="Decline the offer you received."
                    actions={<Button size="sm" variant="outline" onClick={() => setModal('reject')}><X size={14} /> Reject Offer</Button>} />
                )}

                {allow('adm.view') && (
                  <ActionCard icon={Eye} title="Verification Form Profiles" desc="Inspect verification form uploads safely."
                    actions={<Button size="sm" variant="outline" onClick={() => view(() => AdmissionApi.verificationDetails(appId), 'Verification Profiles')}><Eye size={14} /> View Details</Button>} />
                )}

                {allow('adm.verifyDocuments') && !isTerminal && (
                  <ActionCard icon={FileCheck2} title="Verify Documents" desc="Clear or fail the applicant documentation profiles manually."
                    actions={<>
                      <Button size="sm" variant="subtle" onClick={() => run(() => AdmissionApi.verifyDocuments(appId, true), 'Documents Cleared Successfully')}><Check size={14} /> Verify</Button>
                      <Button size="sm" variant="outline" onClick={() => run(() => AdmissionApi.verifyDocuments(appId, false), 'Documents Failed Verification')}><X size={14} /> Fail Check</Button>
                    </>} />
                )}

                {allow('adm.issueAdmissionLetter') && !isTerminal && (
                  <ActionCard icon={ScrollText} title="Admission Letter" desc="Generate final confirmation admission credentials."
                    actions={<Button size="sm" onClick={() => run(() => AdmissionApi.issueAdmissionLetter(appId), 'Official Letter Dispatched')}><ScrollText size={14} /> Issue Letter</Button>} />
                )}

                {allow('adm.finalizeEnrollment') && !isTerminal && (
                  <ActionCard icon={GraduationCap} title="Finalize Enrollment" desc="Formally register profile as a system-wide active student."
                    actions={<Button size="sm" onClick={() => run(() => AdmissionApi.finalizeEnrollment(appId), 'Enrollment Phase Finalized')}><GraduationCap size={14} /> Finalize Enrollment</Button>} />
                )}

                {allow('adm.view') && (
                  <ActionCard icon={Eye} title="Offer Letter" desc="Preview active generation logs of issued invitation files."
                    actions={<Button size="sm" variant="outline" onClick={handleViewOfferLetter}><Eye size={14} /> View Document</Button>} />
                )}

                {allow('adm.withdraw') && !isTerminal && (
                  <ActionCard icon={Ban} title="Withdraw" desc="Trigger voluntary systemic execution drop flags."
                    actions={<Button size="sm" variant="outline" onClick={() => run(() => AdmissionApi.withdraw(appId), 'Application Withdrawn')}><Ban size={14} /> Withdraw</Button>} />
                )}

                {allow('adm.revokeOffer') && !isTerminal && (
                  <ActionCard icon={Undo2} title="Revoke Offer" desc="Force baseline state deletion back into data registry pools."
                    actions={<Button size="sm" variant="outline" onClick={() => { setRevokeReason(''); setModal('revoke') }}><Undo2 size={14} /> Revoke Offer</Button>} />
                )}
              </div>
            </>
          )}
        </>
      )}

      {/* Modals */}
      <IssueOfferModal open={modal === 'offer'} onClose={() => setModal(null)} busy={busy}
        onSubmit={(ref) => run(() => AdmissionApi.issueOffer(appId, ref), 'Offer Issued Successfully')} />
      <AcceptOfferModal open={modal === 'accept'} onClose={() => setModal(null)} busy={busy}
        onSubmit={(p) => run(() => AdmissionApi.acceptOffer(appId, p), 'Offer Accepted')} />
      <ReasonModal open={modal === 'reject'} onClose={() => setModal(null)} busy={busy} title="Reject offer" label="Reason Log Summary"
        onSubmit={(r) => run(() => AdmissionApi.rejectOffer(appId, r), 'Offer Rejected')} />

      <Modal open={modal === 'revoke'} onClose={() => setModal(null)} title="Revoke offer" size="sm">
        <div className="space-y-4">
          <Field label="Reason Clarification"><Input value={revokeReason} onChange={(e) => setRevokeReason(e.target.value)} placeholder="Justification tracking context..." /></Field>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setModal(null)}>Cancel</Button>
            <Button variant="danger" loading={busy} onClick={() => run(() => AdmissionApi.revokeOffer(appId, revokeReason), 'Offer Revoked')}>Revoke Offer</Button>
          </div>
        </div>
      </Modal>

      <Modal open={!!viewer} onClose={() => setViewer(null)} title={viewer?.title} size="md">
        {!viewer?.data ? <EmptyState title="No attributes detected" /> : (
          <div className="space-y-2 text-sm">
            {typeof viewer.data === 'object'
              ? Object.entries(viewer.data).map(([k, v]) => (
                  <div key={k} className="flex justify-between gap-4 py-1.5 border-b" style={{ borderColor: 'var(--border)' }}>
                    <span className="font-mono text-xs" style={{ color: 'var(--text-faint)' }}>{k}</span>
                    <span className="text-right font-medium" style={{ color: 'var(--text)' }}>{v == null ? '—' : String(v)}</span>
                  </div>
                ))
              : <p style={{ color: 'var(--text)' }}>{String(viewer.data)}</p>}
          </div>
        )}
      </Modal>
    </div>
  )
}

const DOC_TYPES = [
  { key: 'TENTH', label: '10th Marksheet', flag: 'tenthMarksheetUploaded' },
  { key: 'TWELFTH', label: '12th Marksheet', flag: 'twelfthMarksheetUploaded' },
  { key: 'AADHAR', label: 'Aadhar Card', flag: 'aadharCardUploaded' },
]

function DocumentsCard({ appId, role, summary, onUploaded, isAdmin }) {
  const toast = useToast()
  const [pending, setPending] = useState({})
  const [uploadingType, setUploadingType] = useState(null)
  const [viewerUrl, setViewerUrl] = useState(null)
  const [viewerOpen, setViewerOpen] = useState(false)

  const pickFile = (docType, file) => setPending((p) => ({ ...p, [docType]: file }))

  const upload = async (docType) => {
    const file = pending[docType]
    if (!file) return toast.error('Choose a file first')
    setUploadingType(docType)
    try {
      await AdmissionApi.uploadDocument(appId, docType, file)
      toast.success('Document uploaded')
      setPending((p) => ({ ...p, [docType]: null }))
      onUploaded?.()
    } catch (e) {
      toast.error(apiMessage(e))
    } finally {
      setUploadingType(null)
    }
  }

  const viewDocument = async (docType) => {
    try {
      const res = await api.get(`/admissions/${appId}/documents/${docType}`, { responseType: 'blob' })
      setViewerUrl(URL.createObjectURL(res.data))
      setViewerOpen(true)
    } catch (e) {
      toast.error(apiMessage(e, 'Could not load document'))
    }
  }

  return (
    <Card className="p-6 mb-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-display font-semibold" style={{ color: 'var(--text)' }}>Documents</h3>
        {summary && <Badge value={summary.documentsVerified ? 'DOCUMENTS_VERIFIED' : 'PENDING'} />}
      </div>
      <div className="grid sm:grid-cols-3 gap-4">
        {DOC_TYPES.map(({ key, label, flag }) => {
          const uploaded = !!summary?.[flag]
          const verified = !!summary?.documentsVerified
          return (
            <div key={key} className="rounded-xl border p-4 space-y-3" style={{ borderColor: 'var(--border)' }}>
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold" style={{ color: 'var(--text)' }}>{label}</p>
                <Badge value={verified && uploaded ? 'VERIFIED' : uploaded ? 'RECEIVED' : 'PENDING'} />
              </div>

              {uploaded && (
                <Button size="sm" variant="subtle" onClick={() => viewDocument(key)}><Eye size={13} /> View</Button>
              )}

              {!isAdmin && (
                <div className="space-y-2">
                  <input
                    type="file"
                    accept="image/*,.pdf"
                    className="field text-xs"
                    onChange={(e) => pickFile(key, e.target.files?.[0] || null)}
                  />
                  <Button size="sm" onClick={() => upload(key)} loading={uploadingType === key}>
                    <FileUp size={13} /> {uploaded ? 'Re-upload' : 'Upload'}
                  </Button>
                </div>
              )}
            </div>
          )
        })}
      </div>

      <Modal open={viewerOpen} onClose={() => setViewerOpen(false)} title="Document preview">
        {viewerUrl && <img src={viewerUrl} alt="Uploaded document" className="w-full rounded-xl" />}
      </Modal>
    </Card>
  )
}

function ActionCard({ icon: Icon, title, desc, actions }) {
  return (
    <Card className="p-5 flex flex-col justify-between border hover:shadow-sm transition" style={{ borderColor: 'var(--border)' }}>
      <div>
        <div className="w-10 h-10 rounded-xl grid place-items-center bg-emerald-500/10 text-emerald-400 mb-3"><Icon size={18} /></div>
        <p className="font-semibold text-sm" style={{ color: 'var(--text)' }}>{title}</p>
        <p className="text-xs mt-1 mb-4" style={{ color: 'var(--text-muted)' }}>{desc}</p>
      </div>
      <div className="flex flex-wrap gap-2 pt-2">{actions}</div>
    </Card>
  )
}

function IssueOfferModal({ open, onClose, onSubmit, busy }) {
  const [ref, setRef] = useState('')
  return (
    <Modal open={open} onClose={onClose} title="Issue Admission Offer" size="sm">
      <div className="space-y-4">
        <Field label="Fee Track Reference ID"><Input value={ref} onChange={(e) => setRef(e.target.value)} placeholder="e.g. FEE-REF-2026" /></Field>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button loading={busy} onClick={() => onSubmit(ref)}>Confirm & Issue</Button>
        </div>
      </div>
    </Modal>
  )
}

function AcceptOfferModal({ open, onClose, onSubmit, busy }) {
  const [f, setF] = useState({ fatherName: '', motherName: '', identificationNumber: '', permanentAddress: '' })
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  return (
    <Modal open={open} onClose={onClose} title="Accept Admission Offer">
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <Field label="Father's Legal Name"><Input value={f.fatherName} onChange={set('fatherName')} placeholder="Father's Name" /></Field>
          <Field label="Mother's Legal Name"><Input value={f.motherName} onChange={set('motherName')} placeholder="Mother's Name" /></Field>
        </div>
        <Field label="Identification ID Document Reference"><Input value={f.identificationNumber} onChange={set('identificationNumber')} placeholder="ID Card Number" /></Field>
        <Field label="Permanent Residential Address"><Input value={f.permanentAddress} onChange={set('permanentAddress')} placeholder="Full Physical Location" /></Field>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button loading={busy} onClick={() => onSubmit(f)}>Accept Invitation</Button>
        </div>
      </div>
    </Modal>
  )
}

function ReasonModal({ open, onClose, onSubmit, busy, title, label }) {
  const [r, setR] = useState('')
  return (
    <Modal open={open} onClose={onClose} title={title} size="sm">
      <div className="space-y-4">
        <Field label={label}><Input value={r} onChange={(e) => setR(e.target.value)} placeholder="Justification tracking context..." /></Field>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button variant="danger" loading={busy} onClick={() => onSubmit(r)}>{title}</Button>
        </div>
      </div>
    </Modal>
  )
}

 