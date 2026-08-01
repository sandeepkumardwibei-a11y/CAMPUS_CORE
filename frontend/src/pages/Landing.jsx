import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { GraduationCap, ArrowRight, BookOpen, Users, CalendarCheck, Award } from 'lucide-react'
import ThemeToggle from '../components/ThemeToggle'
import { TiltCard } from '../components/ui/extras'

const QUOTES = [
  { text: 'Education is the most powerful weapon which you can use to change the world.', author: 'Nelson Mandela' },
  { text: 'The beautiful thing about learning is that no one can take it away from you.', author: 'B.B. King' },
  { text: 'Live as if you were to die tomorrow. Learn as if you were to live forever.', author: 'Mahatma Gandhi' },
  { text: 'An investment in knowledge pays the best interest.', author: 'Benjamin Franklin' },
  { text: 'The roots of education are bitter, but the fruit is sweet.', author: 'Aristotle' },
  { text: 'Develop a passion for learning. If you do, you will never cease to grow.', author: 'Anthony J. D’Angelo' },
]

const FEATURES = [
  { icon: BookOpen, title: 'Programs & Courses', desc: 'Structured departments, programs and a rich course catalogue.' },
  { icon: Users, title: 'Admissions to Alumni', desc: 'Manage the full student lifecycle in one place.' },
  { icon: CalendarCheck, title: 'Exams & Attendance', desc: 'Schedule exams, track attendance and publish results.' },
  { icon: Award, title: 'Academic Standing', desc: 'Automatic CGPA-based rankings and insights.' },
]

export default function Landing() {
  const [i, setI] = useState(0)

  useEffect(() => {
    const id = setInterval(() => setI((n) => (n + 1) % QUOTES.length), 4500)
    return () => clearInterval(id)
  }, [])

  return (
    <div className="min-h-screen relative overflow-hidden">
      <div className="aurora-bg" />
      <div className="absolute top-4 right-4 z-20"><ThemeToggle /></div>

      <div className="relative z-10">
        {/* Nav */}
        <header className="flex items-center justify-between px-6 sm:px-10 h-20">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-xl grid place-items-center gradient-btn text-white shadow-lg shadow-emerald-500/30">
              <GraduationCap size={22} />
            </div>
            <div>
              <p className="font-display font-bold text-xl leading-none gradient-text">CampusCore</p>
              <p className="text-[10px] tracking-wide" style={{ color: 'var(--text-faint)' }}>Campus Management</p>
            </div>
          </div>
         
        </header>

        {/* Hero */}
        <section className="px-6 sm:px-10 pt-10 sm:pt-16 pb-8 max-w-5xl mx-auto text-center">
          <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold mb-6 bg-emerald-500/10 text-emerald-500 ring-1 ring-emerald-500/20">
            The whole campus, in one system
          </span>
          <h1 className="font-display text-4xl sm:text-6xl font-bold leading-tight" style={{ color: 'var(--text)' }}>
            Learn. Grow. <span className="gradient-text">Achieve.</span>
          </h1>
          <p className="mt-5 text-base sm:text-lg max-w-2xl mx-auto" style={{ color: 'var(--text-muted)' }}>
            Admissions to alumni — programs, courses, exams, attendance, fees, hostel and more,
            unified into a single operational core.
          </p>

          {/* Rotating quote — interactive: tilts toward the cursor */}
          <TiltCard maxTilt={5} glowColor="245,158,11" className="mt-10 glass rounded-2xl px-6 py-8 max-w-2xl mx-auto border" style={{ borderColor: 'var(--border)' }}>
            <div key={i} className="animate-fade-up">
              <p className="font-display text-lg sm:text-2xl font-semibold leading-snug" style={{ color: 'var(--text)' }}>
                “{QUOTES[i].text}”
              </p>
              <p className="mt-3 text-sm font-medium text-emerald-500">— {QUOTES[i].author}</p>
            </div>
            <div className="flex justify-center gap-1.5 mt-6">
              {QUOTES.map((_, idx) => (
                <button key={idx} onClick={() => setI(idx)} aria-label={`Quote ${idx + 1}`}
                  className={`h-1.5 rounded-full transition-all ${idx === i ? 'w-6 bg-emerald-500' : 'w-1.5 bg-current opacity-30'}`}
                  style={{ color: 'var(--text-muted)' }} />
              ))}
            </div>
          </TiltCard>

          <div className="mt-10 flex flex-wrap items-center justify-center gap-3">
            <Link to="/register" className="px-6 py-3 rounded-xl font-semibold gradient-btn text-white shadow-lg shadow-emerald-500/25 inline-flex items-center gap-2">
              Get started <ArrowRight size={18} />
            </Link>
            <Link to="/login" className="px-6 py-3 rounded-xl font-semibold border hover:bg-black/5 dark:hover:bg-white/5 transition"
              style={{ borderColor: 'var(--border-strong)', color: 'var(--text)' }}>
              I already have an account
            </Link>
          </div>
        </section>

        {/* Features */}
        <section className="px-6 sm:px-10 pb-16 max-w-5xl mx-auto grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map(({ icon: Icon, title, desc }) => (
            <div key={title} className="glass rounded-2xl p-5 border animate-fade-up hover:-translate-y-1 transition" style={{ borderColor: 'var(--border)' }}>
              <div className="w-10 h-10 rounded-xl grid place-items-center gradient-btn text-white mb-3"><Icon size={18} /></div>
              <p className="font-display font-semibold" style={{ color: 'var(--text)' }}>{title}</p>
              <p className="text-sm mt-1" style={{ color: 'var(--text-muted)' }}>{desc}</p>
            </div>
          ))}
        </section>

        <footer className="text-center pb-8 text-sm" style={{ color: 'var(--text-faint)' }}>
          © {new Date().getFullYear()} CampusCore
        </footer>
      </div>
    </div>
  )
}
