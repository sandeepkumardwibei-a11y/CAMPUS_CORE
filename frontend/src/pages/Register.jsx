import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { GraduationCap, ArrowRight } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { apiMessage } from '../lib/api'
import { Button, Input, Field, Select } from '../components/ui'
import { ROLES } from '../lib/constants'
import ThemeToggle from '../components/ThemeToggle'

export default function Register() {
  const { register } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '', role: 'STUDENT', departmentId: '' })
  const [loading, setLoading] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const payload = { ...form, departmentId: form.departmentId ? Number(form.departmentId) : null }
      const u = await register(payload)
      toast.success(`Account created — welcome, ${u.name}`)
      navigate('/dashboard', { replace: true })
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
          <Field label="Full name"><Input required value={form.name} onChange={set('name')} placeholder="Ada Lovelace" autoComplete="off" /></Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field label="Email"><Input type="email" required value={form.email} onChange={set('email')} placeholder="you@campus.edu" autoComplete="off" name="registerEmail" /></Field>
            <Field label="Phone"><Input value={form.phone} onChange={set('phone')} placeholder="9000000000" autoComplete="off" /></Field>
          </div>
          <Field label="Password"><Input type="password" required value={form.password} onChange={set('password')} placeholder="At least 6 characters" autoComplete="new-password" name="registerPassword" /></Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field label="Role"><Select options={ROLES} value={form.role} onChange={set('role')} /></Field>
            <Field label="Department ID" hint="Optional"><Input type="number" value={form.departmentId} onChange={set('departmentId')} placeholder="e.g. 1" /></Field>
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
