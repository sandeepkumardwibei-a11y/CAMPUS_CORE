import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { GraduationCap, Send, Search, ArrowRight } from 'lucide-react'
import { AdmissionApi, ProgramApi, DepartmentApi } from '../../lib/services'
import { apiMessage } from '../../lib/api'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Card, Button, Field, Input, Select } from '../../components/ui'
import { useAsync, activeOnly } from '../../lib/hooks'
import { Stepper } from '../../components/ui/extras'
import { ADMISSION_PIPELINE, can, currentAcademicYear } from '../../lib/constants'
import { useAuth } from '../../context/AuthContext'
 
const empty = { applicantName: '', email: '', phone: '', programName: '', departmentName: '', academicYear: currentAcademicYear(), percentageSecured: '' }
// Strips anything that isn't a digit, and caps at 10 digits.
const onlyDigits = (v) => v.replace(/\D/g, '').slice(0, 10)

// The applicant's submitted application id is remembered per-user (survives logout),
// so after they apply we can hide the form and default the tracking box to their id.
function admissionKey(userId) { return `cc-admission-${userId}` }
function loadMyAdmissionId(userId) {
  if (!userId) return ''
  try { return localStorage.getItem(admissionKey(userId)) || '' } catch { return '' }
}
export function saveMyAdmissionId(userId, applicationId) {
  if (!userId || !applicationId) return
  try { localStorage.setItem(admissionKey(userId), String(applicationId)) } catch { /* ignore */ }
}
export function clearMyAdmissionId(userId) {
  if (!userId) return
  try { localStorage.removeItem(admissionKey(userId)) } catch { /* ignore */ }
}

export default function Admissions() {
  const toast = useToast()
  const navigate = useNavigate()
  const { user } = useAuth()
  const role = user?.role
  const canApply = can(role, 'adm.apply')

  // If this applicant has already applied, we remember their application id and
  // switch the page from "apply" mode to "track" mode.
  const [myAppId, setMyAppId] = useState(() => loadMyAdmissionId(user?.userId))
  const alreadyApplied = !!myAppId
  const showApplyCard = canApply && !alreadyApplied

  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const [lookupId, setLookupId] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  // Program & Department options for the applicant dropdowns (only fetched when the form is shown)
  const { data: programsData } = useAsync(() => (showApplyCard ? ProgramApi.all() : Promise.resolve([])), [showApplyCard])
  const { data: departmentsData } = useAsync(() => (showApplyCard ? DepartmentApi.all() : Promise.resolve([])), [showApplyCard])
  // Dropdowns exclude DISCONTINUED programs / non-ACTIVE departments, ascending by id.
  const allPrograms = activeOnly(programsData, 'programId')
  const activeDepartments = activeOnly(departmentsData, 'departmentId')
  const departmentOptions = activeDepartments.map((d) => d.departmentName).filter(Boolean)

  // item 5: once a department is chosen, only show programs offered under it.
  const selectedDept = activeDepartments.find((d) => d.departmentName === form.departmentName)
  const programOptions = allPrograms
    .filter((p) => !selectedDept || p.departmentId === selectedDept.departmentId)
    .map((p) => p.programName)
    .filter(Boolean)

  // Default the tracking box: for an applicant who already applied, use their own
  // remembered application id; for a STUDENT, use their personal id (legacy behaviour).
  useEffect(() => {
    if (alreadyApplied) {
      setLookupId(String(myAppId))
    } else if (role === 'STUDENT') {
      const personalId = user?.applicationId || user?.id || user?.userId || ''
      if (personalId) setLookupId(String(personalId))
    }
  }, [role, user, alreadyApplied, myAppId])

  const apply = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const res = await AdmissionApi.apply({ ...form, percentageSecured: Number(form.percentageSecured) })
      const id = res?.id ?? res?.applicationId
      toast.success(id ? `Application #${id} submitted` : 'Application submitted')
      setForm(empty)
      if (id) {
        // Remember it so the apply card disappears and tracking is pre-filled.
        saveMyAdmissionId(user?.userId, id)
        setMyAppId(String(id))
        navigate(`/admissions/${id}`)
      }
    } catch (err) { toast.error(apiMessage(err)) } finally { setSaving(false) }
  }

  // Secure navigation tracking gateway
  const handleTrackApplication = () => {
    // An applicant who already applied can only open their own remembered application.
    if (alreadyApplied) {
      navigate(`/admissions/${myAppId}`)
      return
    }
    if (role === 'STUDENT') {
      const personalId = String(user?.applicationId || user?.id || user?.userId || '')
      if (personalId && String(lookupId) !== personalId) {
        toast.error("Access denied: You are only permitted to track your own application record.")
        setLookupId(personalId) // Force reset the field back to their actual ID
        return
      }
    }
    navigate(`/admissions/${lookupId}`)
  }

  // Whether the tracking id field should be locked to the user's own id.
  const lockTrackingId = alreadyApplied || role === 'STUDENT'

  return (
    <div>
      <PageHeader icon={GraduationCap} title="Admissions"
        subtitle={showApplyCard ? 'Submit an application, then walk it through the pipeline.'
          : alreadyApplied ? 'You have applied — track your application below.'
          : 'Look up an application and manage its admission workflow.'} />

      {/* Pipeline preview — the signature element */}
      <Card className="p-6 mb-6">
        <p className="label mb-3">The admission journey</p>
        <Stepper steps={ADMISSION_PIPELINE} current="SUBMITTED" />
      </Card>

      <div className={`grid gap-6 ${showApplyCard ? 'lg:grid-cols-3' : 'lg:grid-cols-1'}`}>
        {showApplyCard && (
          <Card className="p-6 lg:col-span-2">
            <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>New application</h3>
            <form onSubmit={apply} className="space-y-4">
              <div className="grid sm:grid-cols-2 gap-4">
                <Field label="Applicant name"><Input required value={form.applicantName} onChange={set('applicantName')} placeholder="Grace Hopper" /></Field>
                <Field label="Email"><Input type="email" required value={form.email} onChange={set('email')} placeholder="grace@applicant.edu" /></Field>
              </div>
              <div className="grid sm:grid-cols-2 gap-4">
                <Field label="Phone">
                  <Input value={form.phone} inputMode="numeric" maxLength={10} placeholder="9000000010"
                    onChange={(e) => setForm({ ...form, phone: onlyDigits(e.target.value) })} />
                </Field>
                <Field label="Academic year"><Input value={form.academicYear} onChange={set('academicYear')} placeholder={currentAcademicYear()} /></Field>
              </div>
              <div className="grid sm:grid-cols-2 gap-4">
                <Field label="Department name" hint="Choose a department first">
                  <Select placeholder="Select a department" options={departmentOptions} value={form.departmentName}
                    onChange={(e) => setForm({ ...form, departmentName: e.target.value, programName: '' })} />
                </Field>
                <Field label="Program name" hint={form.departmentName ? 'Programs offered under this department' : 'Select a department first'}>
                  <Select placeholder={form.departmentName ? 'Select a program' : 'Select a department first'}
                    options={programOptions} value={form.programName} onChange={set('programName')}
                    disabled={!form.departmentName} />
                </Field>
              </div>
              <Field label="Percentage secured"><Input type="number" step="0.1" min={0} max={100} value={form.percentageSecured} onChange={set('percentageSecured')} placeholder="88.5" /></Field>
              <Button type="submit" loading={saving}><Send size={16} /> Submit application</Button>
            </form>
          </Card>
        )}

        <Card className="p-6 h-fit">
          <h3 className="font-display font-semibold mb-4" style={{ color: 'var(--text)' }}>Track an application</h3>
          <p className="text-sm mb-4" style={{ color: 'var(--text-muted)' }}>
            {alreadyApplied ? 'Your submitted application ID — track its progress through the pipeline.'
              : role === 'STUDENT' ? 'Your assigned personal tracking code reference.'
              : 'Enter an application ID to view its status and run workflow actions.'}
          </p>
          <Field label="Application ID">
            <Input
              type="number"
              min={1}
              max={999999}
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
              placeholder="1"
              disabled={lockTrackingId} // Locked to their own id once they've applied / for students
            />
          </Field>
          <Button className="mt-4 w-full" variant="subtle" disabled={!lookupId} onClick={handleTrackApplication}>
            Open application <ArrowRight size={16} />
          </Button>
        </Card>
      </div>
    </div>
  )
}
 