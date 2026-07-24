import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { GraduationCap, Mail, Lock, ArrowRight, Eye, EyeOff } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { apiMessage } from '../lib/api'
import { Button, Input, Field } from '../components/ui'
import ThemeToggle from '../components/ThemeToggle'

export default function Login() {
  const { login } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const loc = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const u = await login(form.email, form.password)
      toast.success(`Welcome back, ${u.name}`)
      const dest = loc.state?.from
      navigate(dest && dest !== '/' && dest !== '/welcome' ? dest : '/dashboard', { replace: true })
    } catch (err) {
      toast.error(apiMessage(err, 'Login failed. Check your credentials.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen relative flex">
      <div className="aurora-bg" />
      <div className="absolute top-4 right-4 z-20"><ThemeToggle /></div>

      {/* Brand panel */}
      <div className="hidden lg:flex flex-col justify-between w-[46%] relative z-10 p-12 text-white overflow-hidden gradient-btn">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-2xl grid place-items-center bg-white/20 backdrop-blur"><GraduationCap size={24} /></div>
          <span className="font-display font-bold text-2xl">CampusCore</span>
        </div>
        <div>
          <h1 className="font-display text-4xl font-bold leading-tight">The whole campus,
in one system.</h1>
          <p className="mt-4 text-white/80 max-w-md">Admissions to alumni — programs, courses, exams, attendance, fees, hostel and more, unified into a single operational core.</p>
        </div>
        <p className="text-white/60 text-sm">© {new Date().getFullYear()} CampusCore</p>
      </div>

      {/* Form panel */}
      <div className="flex-1 relative z-10 flex items-center justify-center p-6">
        <div className="w-full max-w-sm animate-fade-up">
          <div className="lg:hidden flex items-center gap-2.5 mb-8">
            <div className="w-10 h-10 rounded-xl grid place-items-center gradient-btn text-white"><GraduationCap size={20} /></div>
            <span className="font-display font-bold text-xl gradient-text">CampusCore</span>
          </div>
          <h2 className="font-display text-3xl font-bold" style={{ color: 'var(--text)' }}>Welcome back</h2>
          <p className="mt-1.5 mb-8 text-sm" style={{ color: 'var(--text-muted)' }}>Sign in to your CampusCore account.</p>

          <form onSubmit={submit} className="space-y-4" autoComplete="off">
            {/* Hidden decoy fields to discourage aggressive browser autofill */}
            <input type="text" name="prevent_autofill" autoComplete="off" className="hidden" tabIndex={-1} aria-hidden="true" />
            <input type="password" name="password_fake" autoComplete="new-password" className="hidden" tabIndex={-1} aria-hidden="true" />

            <Field label="Email">
              <div className="relative">
                <Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none z-10" style={{ color: 'var(--text-faint)' }} />
                <Input type="email" required placeholder="" autoComplete="off" name="cc-email" style={{ paddingLeft: '2.5rem' }}
                  value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
            </Field>
            <Field label="Password">
              <div className="relative">
                <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none z-10" style={{ color: 'var(--text-faint)' }} />
                <Input type={showPassword ? 'text' : 'password'} required placeholder="" autoComplete="new-password" name="cc-password"
                  style={{ paddingLeft: '2.5rem', paddingRight: '2.75rem' }}
                  value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
                <button type="button" onClick={() => setShowPassword((s) => !s)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 z-10 p-0.5 rounded hover:opacity-70 transition"
                  style={{ color: 'var(--text-faint)' }}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}>
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </Field>
            <Button type="submit" loading={loading} className="w-full" size="lg">
              Sign in <ArrowRight size={18} />
            </Button>
          </form>

          <p className="mt-6 text-sm text-center" style={{ color: 'var(--text-muted)' }}>
            New here? <Link to="/register" className="font-semibold text-emerald-400 hover:underline">Create an account</Link>
          </p>
        </div>
      </div>
    </div>
  )
}