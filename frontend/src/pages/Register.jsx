import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { GraduationCap, ArrowRight } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { apiMessage } from '../lib/api'
import { Button, Input, Field, Select } from '../components/ui'
import { REGISTERABLE_ROLES } from '../lib/constants'
import ThemeToggle from '../components/ThemeToggle'

// Strips anything that isn't a digit, and caps at 10 digits — used on every phone field.
const onlyDigits = (v) => v.replace(/\D/g, '').slice(0, 10)
// Mirrors the backend RegisterRequest password @Pattern: upper, lower, digit, special, 8+ chars.
const PASSWORD_PATTERN = '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!.,_-]).{8,}$'
// Letters, spaces, apostrophes, hyphens, and periods only — no digits or other symbols.
const NAME_PATTERN = "^[A-Za-z][A-Za-z .'-]{1,99}$"

export default function Register() {
  const { register } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '', role: 'STUDENT', departmentId: '' })
  const [loading, setLoading] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  // Mirrors the backend rule: Department ID is mandatory only for STUDENT and FACULTY.
  const isDeptRequired = form.role === 'STUDENT' || form.role === 'FACULTY'

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const payload = { ...form, departmentId: form.departmentId ? Number(form.departmentId) : null }
      await register(payload)
      toast.success('Account created! Your account is pending admin approval — you can log in once an admin activates it.')
      navigate('/login', { replace: true })
    } catch (err) {
      toast.error(apiMessage(err, 'Registration failed.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen relative flex items-center justify-center p-6">
      <div className="aurora-bg" />
      <div className="absolute top-4 right-4 z-20"><ThemeToggle /></div>
      <div className="w-full max-w-md glass rounded-3xl p-8 relative z-10 animate-fade-up">
        <div className="flex items-center gap-2.5 mb-6">
          <div className="w-10 h-10 rounded-xl grid place-items-center gradient-btn text-white"><GraduationCap size={20} /></div>
          <span className="font-display font-bold text-xl gradient-text">CampusCore</span>
        </div>
        <h2 className="font-display text-2xl font-bold" style={{ color: 'var(--text)' }}>Create your account</h2>
        <p className="mt-1 mb-6 text-sm" style={{ color: 'var(--text-muted)' }}>Join the campus operating system.</p>

        <form onSubmit={submit} className="space-y-4" autoComplete="off">
          {/* Decoy fields: invisible to the user, but Chrome/Edge's autofill heuristics latch onto
              the FIRST matching email/password input on the page. These absorb that behavior so
              the real fields below stay blank on load. */}
          <div aria-hidden="true" style={{ position: 'absolute', width: 0, height: 0, overflow: 'hidden', opacity: 0, pointerEvents: 'none' }}>
            <input type="text" name="fake-name" tabIndex={-1} autoComplete="off" />
            <input type="email" name="fake-email" tabIndex={-1} autoComplete="off" />
            <input type="password" name="fake-password" tabIndex={-1} autoComplete="new-password" />
          </div>

          <Field label="Full name" hint="3–50 letters — spaces, hyphens, and apostrophes allowed">
            <Input required minLength={3} maxLength={50} pattern={NAME_PATTERN}
              title="3-50 letters only (spaces, hyphens, apostrophes and periods allowed) — no digits or other symbols"
              autoComplete="off" name="register-name"
              value={form.name} onChange={set('name')} placeholder="Ada Lovelace" />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field label="Email">
              <Input type="email" required autoComplete="off" name="register-email"
                value={form.email} onChange={set('email')} placeholder="you@campus.edu" />
            </Field>
            <Field label="Phone">
              <Input value={form.phone} inputMode="numeric" maxLength={10} placeholder="9000000000"
                autoComplete="off" name="register-phone"
                onChange={(e) => setForm({ ...form, phone: onlyDigits(e.target.value) })} />
            </Field>
          </div>
          <Field label="Password" hint="At least 8 characters, with uppercase, lowercase, a number, and a special character">
            <Input type="password" required pattern={PASSWORD_PATTERN} title="At least 8 characters, including uppercase, lowercase, a number, and a special character"
              autoComplete="new-password" name="register-password"
              value={form.password} onChange={set('password')} placeholder="At least 8 characters" />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field label="Role"><Select options={REGISTERABLE_ROLES} value={form.role} onChange={set('role')} /></Field>
            <Field label="Department ID" hint={isDeptRequired ? 'Required for this role' : 'Optional'}>
              <Input type="number" min={1} max={999999} required={isDeptRequired} autoComplete="off"
                value={form.departmentId} onChange={set('departmentId')} placeholder="e.g. 1" />
            </Field>
          </div>
          <Button type="submit" loading={loading} className="w-full" size="lg">Create account <ArrowRight size={18} /></Button>
        </form>

        <p className="mt-6 text-sm text-center" style={{ color: 'var(--text-muted)' }}>
          Already have an account? <Link to="/login" className="font-semibold text-emerald-400 hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
