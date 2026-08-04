import { useNavigate } from 'react-router-dom'
import { GraduationCap, LogIn, Home, PartyPopper } from 'lucide-react'
import { Button } from '../components/ui'
import ThemeToggle from '../components/ThemeToggle'

// Shown after an applicant finalises their enrollment. The applicant has already
// been logged out by this point, so this is a public celebratory landing page.
export default function EnrollmentSuccess() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen relative flex items-center justify-center p-6 overflow-hidden">
      <div className="aurora-bg" />
      <div className="absolute top-4 right-4 z-20"><ThemeToggle /></div>

      {/* Soft celebratory glow */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{ background: 'radial-gradient(600px circle at 50% 40%, rgba(16,185,129,.18), transparent 60%)' }}
      />

      <div className="relative z-10 w-full max-w-lg text-center glass rounded-3xl p-10 animate-fade-up">
        {/* Animated badge */}
        <div className="relative mx-auto mb-8 w-24 h-24">
          <span className="absolute inset-0 rounded-full bg-emerald-500/20 animate-ping" />
          <span className="absolute inset-0 rounded-full bg-emerald-500/10" />
          <div className="relative w-24 h-24 rounded-full grid place-items-center gradient-btn text-white shadow-lg shadow-emerald-500/30 animate-[popIn_.6s_ease-out]">
            <GraduationCap size={44} />
          </div>
        </div>

        <div className="flex items-center justify-center gap-2 mb-3">
          <PartyPopper className="text-amber-400 animate-bounce" size={22} />
          <span className="text-xs font-semibold px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-500 ring-1 ring-emerald-500/25">
            Enrollment complete
          </span>
          <PartyPopper className="text-amber-400 animate-bounce" size={22} />
        </div>

        <h1 className="font-display text-3xl sm:text-4xl font-bold gradient-text mb-4">
          Thank you & welcome to CampusCore!
        </h1>

        <p className="text-sm sm:text-base leading-relaxed mb-8" style={{ color: 'var(--text-muted)' }}>
          Your enrollment has been finalised and your student profile is now active. It has been a
          pleasure guiding you through admissions — from application to acceptance to today. We're
          genuinely excited to have you join our campus community. Log in again with your student
          account to explore your courses, timetable, fees, hostel and everything CampusCore has to offer.
          Here's to a wonderful journey ahead. 🎓
        </p>

        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Button onClick={() => navigate('/login')}><LogIn size={16} /> Go to Login</Button>
          <Button variant="outline" onClick={() => navigate('/')}><Home size={16} /> Go to Home</Button>
        </div>
      </div>

      {/* Local keyframes for the badge pop-in (aurora/fade-up already exist globally). */}
      <style>{`
        @keyframes popIn {
          0% { transform: scale(.4); opacity: 0; }
          60% { transform: scale(1.1); opacity: 1; }
          100% { transform: scale(1); }
        }
      `}</style>
    </div>
  )
}
