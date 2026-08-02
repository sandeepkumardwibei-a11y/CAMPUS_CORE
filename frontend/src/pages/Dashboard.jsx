import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Layers, BookOpen, Bell, GraduationCap, Wallet, CalendarCheck,
  BedDouble, FileSpreadsheet, ArrowUpRight, Sparkles, UserCog,
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { ProgramApi, CourseApi, NotificationApi, HostelApi, FeeApi, UserApi } from '../lib/services'
import { StatCard } from '../components/ui/extras'
import { Card } from '../components/ui'
import { navForRole, can } from '../lib/constants'

// Coerce Spring Page / array / single-object responses into an array for counting.
function asArrayLike(d) {
  if (Array.isArray(d)) return d
  if (d?.content && Array.isArray(d.content)) return d.content
  if (d && typeof d === 'object') return Object.values(d).every((v) => v == null) ? [] : [d]
  return []
}

export default function Dashboard() {
  const { user } = useAuth()
  const [stats, setStats] = useState({ programs: '—', courses: '—', unread: '—', rooms: '—', pendingApprovals: '—' })

  useEffect(() => {
    const num = (v) => (Array.isArray(v) ? v.length : (v?.length ?? v?.totalElements ?? v ?? 0))
    const countActive = (list, activeVal) => asArrayLike(list).filter((x) => {
      const st = String(x?.status ?? '').toUpperCase()
      return st === '' ? true : st === activeVal
    }).length

    const load = () => {
      ProgramApi.all()
        .then((d) => setStats((s) => ({ ...s, programs: countActive(d, 'ACTIVE') })))
        .catch(() => {})
      CourseApi.all()
        .then((d) => setStats((s) => ({ ...s, courses: countActive(d, 'ACTIVE') })))
        .catch(() => {})
      if (can(user?.role, 'hostel.availableRooms')) {
        HostelApi.availableRooms().then((d) => setStats((s) => ({ ...s, rooms: num(d) }))).catch(() => {})
      }
      // Pending-approval count — admin only (mirrors 'users.updateStatus', the only
      // role that can actually act on a pending account).
      if (can(user?.role, 'users.updateStatus')) {
        UserApi.list()
          .then((d) => setStats((s) => ({
            ...s,
            pendingApprovals: asArrayLike(d).filter((u) => String(u?.status ?? '').toUpperCase() === 'PENDING').length,
          })))
          .catch(() => {})
      }
      if (user?.userId) {
        NotificationApi.unreadCount(user.userId)
          .then((d) => setStats((s) => ({ ...s, unread: typeof d === 'number' ? d : d?.count ?? d?.unread ?? 0 })))
          .catch(() => {})
      }
    }

    load()
    // Keep the dashboard live: refresh when the tab regains focus, on a periodic
    // poll, and whenever another page signals a data change (e.g. notifications read).
    const onFocus = () => load()
    const onDataChange = () => load()
    window.addEventListener('focus', onFocus)
    window.addEventListener('cc:data-changed', onDataChange)
    const timer = setInterval(load, 20000)
    return () => {
      window.removeEventListener('focus', onFocus)
      window.removeEventListener('cc:data-changed', onDataChange)
      clearInterval(timer)
    }
  }, [user?.userId, user?.role])

  const quick = navForRole(user?.role).filter((n) => n.to !== '/').slice(0, 8)
  const hour = new Date().getHours()
  const greet = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'

  return (
    <div>
      {/* Hero */}
      <Card className="relative overflow-hidden p-6 sm:p-8 mb-6 animate-fade-up">
        <div className="absolute -top-16 -right-16 w-52 h-52 rounded-full bg-gradient-to-br from-emerald-500/30 to-green-500/20 blur-2xl" />
        <div className="relative">
          <div className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 mb-3">
            <Sparkles size={13} /> {user?.role?.replace(/_/g, ' ')}
          </div>
          <h1 className="font-display text-3xl font-bold" style={{ color: 'var(--text)' }}>
            {greet}, {user?.name?.split(' ')[0]} 👋
          </h1>
          <p className="mt-1.5 max-w-lg" style={{ color: 'var(--text-muted)' }}>
            Here's your campus at a glance. Jump into any module below to manage the day.
          </p>
        </div>
      </Card>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Active programs" value={stats.programs} icon={Layers} tone="indigo" />
        <StatCard label="Courses offered" value={stats.courses} icon={BookOpen} tone="fuchsia" />
        {can(user?.role, 'hostel.availableRooms') && (
          <StatCard label="Rooms available" value={stats.rooms} icon={BedDouble} tone="emerald" />
        )}
        {can(user?.role, 'users.updateStatus') && (
          <Link to="/users?status=PENDING">
            <StatCard label="Pending approvals" value={stats.pendingApprovals} icon={UserCog} tone="amber" />
          </Link>
        )}
        <StatCard label="Unread alerts" value={stats.unread} icon={Bell} tone="amber" />
      </div>

      {/* Quick actions */}
      <h2 className="font-display text-lg font-semibold mb-4" style={{ color: 'var(--text)' }}>Quick access</h2>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        {quick.map(({ to, label, icon: Icon }) => (
          <Link key={to} to={to}
            className="glass rounded-2xl p-5 group hover:-translate-y-0.5 transition relative overflow-hidden">
            <div className="w-11 h-11 rounded-xl grid place-items-center bg-emerald-500/10 text-emerald-400 group-hover:gradient-btn group-hover:text-white transition mb-3">
              <Icon size={20} />
            </div>
            <p className="font-semibold text-sm" style={{ color: 'var(--text)' }}>{label}</p>
            <ArrowUpRight size={16} className="absolute top-4 right-4 opacity-0 group-hover:opacity-100 text-emerald-400 transition" />
          </Link>
        ))}
      </div>
    </div>
  )
}
