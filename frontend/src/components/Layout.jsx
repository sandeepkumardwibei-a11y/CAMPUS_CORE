import { useState, useEffect } from 'react'
import { NavLink, useLocation, useNavigate, Outlet } from 'react-router-dom'
import { Menu, X, LogOut, GraduationCap, Bell } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { navForRole } from '../lib/constants'
import { NotificationApi } from '../lib/services'
import ThemeToggle from './ThemeToggle'

function roleLabel(r) {
  return (r || '').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}

// Selectable interactive avatars (emoji-based, no external assets needed).
const AVATARS = ['🧑‍🎓', '👩‍🎓', '🧑‍🏫', '👩‍🏫', '🧑‍💼', '👩‍💼', '🦸', '🦹', '🧙', '🐼', '🦊', '🐯', '🦁', '🐨', '🐵', '🚀', '⭐', '🎓', '📚', '💡']

export default function Layout() {
  const { user, logout, updateAvatar } = useAuth()
  const nav = navForRole(user?.role)
  const [open, setOpen] = useState(false)
  const [unread, setUnread] = useState(0)
  const [avatarOpen, setAvatarOpen] = useState(false)
  const loc = useLocation()
  const navigate = useNavigate()

  useEffect(() => { setOpen(false) }, [loc.pathname])

  useEffect(() => {
    let alive = true
    if (!user?.userId) return
    const refresh = () => {
      NotificationApi.unreadCount(user.userId)
        .then((d) => alive && setUnread(typeof d === 'number' ? d : d?.count ?? d?.unread ?? 0))
        .catch(() => {})
    }
    refresh()
    const onChange = () => refresh()
    window.addEventListener('cc:data-changed', onChange)
    window.addEventListener('focus', onChange)
    return () => {
      alive = false
      window.removeEventListener('cc:data-changed', onChange)
      window.removeEventListener('focus', onChange)
    }
  }, [user?.userId, loc.pathname])

  const doLogout = () => { logout(); navigate('/login') }

  const SidebarBody = (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2.5 px-5 h-16 shrink-0">
        <div className="w-9 h-9 rounded-xl grid place-items-center gradient-btn text-white shadow-lg shadow-emerald-500/30">
          <GraduationCap size={20} />
        </div>
        <div>
          <p className="font-display font-bold text-lg leading-none gradient-text">CampusCore</p>
          <p className="text-[10px] tracking-wide" style={{ color: 'var(--text-faint)' }}>Campus Management</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-2 space-y-0.5">
        {nav
          .filter(({ to }) => !(user?.role === 'FACULTY' && to === '/registrations'))
          .map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition group ${
                  isActive ? 'gradient-btn text-white shadow-md shadow-emerald-500/25' : 'hover:bg-black/5 dark:hover:bg-white/5'
                }`
              }
              style={({ isActive }) => (isActive ? undefined : { color: 'var(--text-muted)' })}>
              <Icon size={18} className="shrink-0" />
              <span className="truncate">{label}</span>
              {to === '/notifications' && unread > 0 && (
                <span className="ml-auto text-[10px] font-bold bg-rose-500 text-white rounded-full min-w-[18px] h-[18px] grid place-items-center px-1">
                  {unread > 99 ? '99+' : unread}
                </span>
              )}
            </NavLink>
          ))}
      </nav>

      <div className="p-3 border-t relative" style={{ borderColor: 'var(--border)' }}>
        {avatarOpen && (
          <>
            <div className="fixed inset-0 z-30" onClick={() => setAvatarOpen(false)} />
            <div className="absolute bottom-full left-3 right-3 mb-2 z-40 glass rounded-2xl border p-3 shadow-xl"
              style={{ borderColor: 'var(--border)' }}>
              <p className="text-xs font-semibold mb-2" style={{ color: 'var(--text-muted)' }}>Choose your avatar</p>
              <div className="grid grid-cols-5 gap-1.5">
                {AVATARS.map((a) => (
                  <button key={a} onClick={() => { updateAvatar(a); setAvatarOpen(false) }}
                    className={`aspect-square rounded-xl text-xl grid place-items-center transition hover:scale-110
                      ${user?.avatar === a ? 'ring-2 ring-emerald-500 bg-emerald-500/10' : 'hover:bg-black/5 dark:hover:bg-white/5'}`}>
                    {a}
                  </button>
                ))}
              </div>
            </div>
          </>
        )}
        <div className="flex items-center gap-3 px-2 py-2 rounded-xl">
          <button onClick={() => setAvatarOpen((o) => !o)} title="Change avatar"
            className="w-9 h-9 rounded-full grid place-items-center bg-gradient-to-br from-emerald-500 to-green-500 text-white font-bold text-sm shrink-0 transition hover:scale-105 hover:ring-2 hover:ring-emerald-400/50 overflow-hidden">
            {user?.avatar
              ? <span className="text-lg leading-none">{user.avatar}</span>
              : (user?.name || '?').charAt(0).toUpperCase()}
          </button>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold truncate" style={{ color: 'var(--text)' }}>{user?.name}</p>
            <p className="text-[11px] truncate" style={{ color: 'var(--text-faint)' }}>{roleLabel(user?.role)}</p>
          </div>
          <button onClick={doLogout} title="Log out" className="p-2 rounded-lg hover:bg-rose-500/10 hover:text-rose-500 transition" style={{ color: 'var(--text-muted)' }}>
            <LogOut size={17} />
          </button>
        </div>
      </div>
    </div>
  )

  return (
    <div className="h-screen overflow-hidden relative">
      <div className="aurora-bg" />
      <div className="relative z-10 flex h-screen">
        {/* Desktop sidebar — its own scrollbar, independent of the main content */}
        <aside className="hidden lg:flex w-64 shrink-0 glass border-r m-0 flex-col h-screen overflow-y-auto" style={{ borderColor: 'var(--border)' }}>
          {SidebarBody}
        </aside>

        {/* Mobile drawer */}
        {open && (
          <div className="lg:hidden fixed inset-0 z-50">
            <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setOpen(false)} />
            <aside className="absolute left-0 top-0 bottom-0 w-72 glass animate-fade-up">{SidebarBody}</aside>
          </div>
        )}

        <div className="flex-1 flex flex-col min-w-0 h-screen overflow-hidden">
          {/* Topbar */}
          <header className="h-16 shrink-0 glass border-b flex items-center gap-3 px-4 sm:px-6 z-30" style={{ borderColor: 'var(--border)' }}>
            <button onClick={() => setOpen(true)} className="lg:hidden p-2 -ml-1"><Menu size={22} /></button>
            <p className="font-display font-semibold hidden sm:block" style={{ color: 'var(--text)' }}>
              {nav.find((n) => n.to === loc.pathname || (n.to !== '/' && loc.pathname.startsWith(n.to)))?.label || 'Dashboard'}
            </p>
            <div className="flex-1" />
            <NavLink to="/notifications" className="relative w-10 h-10 rounded-xl grid place-items-center border hover:bg-black/5 dark:hover:bg-white/5 transition" style={{ borderColor: 'var(--border-strong)', color: 'var(--text)' }}>
              <Bell size={18} />
              {unread > 0 && <span className="absolute -top-1 -right-1 w-4 h-4 text-[9px] font-bold bg-rose-500 text-white rounded-full grid place-items-center">{unread > 9 ? '9+' : unread}</span>}
            </NavLink>
            <ThemeToggle />
          </header>

          <main className="flex-1 overflow-y-auto">
            <div className="p-4 sm:p-6 lg:p-8 max-w-[1400px] w-full mx-auto">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </div>
  )
}