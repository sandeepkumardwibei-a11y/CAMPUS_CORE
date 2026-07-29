import { Loader2, X, Inbox } from 'lucide-react'
import { STATUS_TONE, toneClasses } from '../../lib/constants'

export function Button({ variant = 'primary', size = 'md', className = '', children, loading, ...props }) {
  const base = 'inline-flex items-center justify-center gap-2 font-semibold rounded-xl transition disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap'
  const sizes = { sm: 'text-xs px-3 py-1.5', md: 'text-sm px-4 py-2.5', lg: 'text-base px-5 py-3' }
  const variants = {
    primary: 'gradient-btn text-white shadow-lg shadow-emerald-500/25',
    ghost: 'hover:bg-black/5 dark:hover:bg-white/5',
    outline: 'border',
    danger: 'bg-rose-500/90 hover:bg-rose-500 text-white',
    subtle: 'bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500/20',
  }
  const style = variant === 'outline' ? { borderColor: 'var(--border-strong)', color: 'var(--text)' } : undefined
  return (
    <button className={`${base} ${sizes[size]} ${variants[variant]} ${className}`} style={style} disabled={loading || props.disabled} {...props}>
      {loading && <Loader2 size={16} className="animate-spin" />}
      {children}
    </button>
  )
}

export function Card({ className = '', children, ...props }) {
  return <div className={`glass rounded-2xl ${className}`} {...props}>{children}</div>
}

export function Badge({ value, children }) {
  const v = String(value ?? children ?? '').toUpperCase()
  const tone = STATUS_TONE[v] || 'indigo'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-semibold ring-1 ${toneClasses[tone]}`}>
      {String(value ?? children ?? '—').replace(/_/g, ' ')}
    </span>
  )
}

export function Field({ label, children, hint, className = '' }) {
  return (
    <label className={`block ${className}`}>
      {label && <span className="label">{label}</span>}
      {children}
      {hint && <span className="block text-xs mt-1" style={{ color: 'var(--text-faint)' }}>{hint}</span>}
    </label>
  )
}

export function Input({ className = '', type, ...props }) {
  // Default length constraints, applied only when the page didn't already set its own
  // (an explicit prop always wins because it's spread after these defaults).
  // - Generic text/email fields: 3-50 characters.
  // - Password fields: 8-50 characters.
  // Number/date/etc. inputs are left untouched — a blanket 3-50 "length" doesn't make sense
  // for things like fee amounts, marks, credits, or percentages, and would wrongly reject valid values.
  let lengthDefaults = {}
  if (type === 'password') lengthDefaults = { minLength: 8, maxLength: 50 }
  else if (type === undefined || type === 'text' || type === 'email' || type === 'tel' || type === 'search') {
    lengthDefaults = { minLength: 3, maxLength: 50 }
  }
  return <input type={type} className={`field ${className}`.trim()} {...lengthDefaults} {...props} />
}
export function Textarea({ className = '', ...props }) { return <textarea className={`field ${className}`.trim()} rows={3} {...props} /> }
export function Select({ options = [], placeholder, children, className = '', ...props }) {
  return (
    <select className={`field ${className}`.trim()} {...props}>
      {placeholder && <option value="">{placeholder}</option>}
      {children || options.map((o) => {
        const val = typeof o === 'object' ? o.value : o
        const lab = typeof o === 'object' ? o.label : String(o).replace(/_/g, ' ')
        return <option key={val} value={val}>{lab}</option>
      })}
    </select>
  )
}

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16" style={{ color: 'var(--text-muted)' }}>
      <Loader2 className="animate-spin" size={20} /> <span className="text-sm">{label}</span>
    </div>
  )
}

export function EmptyState({ title = 'Nothing here yet', hint, icon: Icon = Inbox, action }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-14 px-4">
      <div className="w-14 h-14 rounded-2xl grid place-items-center bg-emerald-500/10 text-emerald-400 mb-4">
        <Icon size={24} />
      </div>
      <p className="font-semibold" style={{ color: 'var(--text)' }}>{title}</p>
      {hint && <p className="text-sm mt-1 max-w-sm" style={{ color: 'var(--text-muted)' }}>{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function PageHeader({ title, subtitle, actions, icon: Icon }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-4 mb-6">
      <div className="flex items-start gap-3">
        {Icon && (
          <div className="w-11 h-11 rounded-2xl grid place-items-center gradient-btn text-white shadow-lg shadow-emerald-500/25 shrink-0">
            <Icon size={22} />
          </div>
        )}
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight" style={{ color: 'var(--text)' }}>{title}</h1>
          {subtitle && <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>{subtitle}</p>}
        </div>
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function Modal({ open, onClose, title, children, size = 'md' }) {
  if (!open) return null
  const w = { sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-2xl', xl: 'max-w-4xl' }[size]
  return (
    <div className="fixed inset-0 z-[90] flex items-start justify-center p-4 sm:p-6 overflow-y-auto"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="fixed inset-0 bg-black/50 backdrop-blur-sm" />
      <div className={`glass rounded-2xl w-full ${w} relative my-8 animate-fade-up`}>
        <div className="flex items-center justify-between px-5 py-4 border-b" style={{ borderColor: 'var(--border)' }}>
          <h3 className="font-display font-semibold text-lg" style={{ color: 'var(--text)' }}>{title}</h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100 p-1"><X size={18} /></button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  )
}

// Table primitives — responsive with horizontal scroll
export function Table({ head, children }) {
  return (
    <div className="overflow-x-auto -mx-1 px-1">
      <table className="w-full text-sm border-separate border-spacing-y-1.5 min-w-[560px]">
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={i} className="text-left font-semibold text-xs uppercase tracking-wide px-4 py-2"
                style={{ color: 'var(--text-faint)' }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

export function Row({ children }) {
  return (
    <tr className="[&>td]:bg-black/[0.02] dark:[&>td]:bg-white/[0.03] [&>td:first-child]:rounded-l-xl [&>td:last-child]:rounded-r-xl hover:[&>td]:bg-emerald-500/[0.06] transition">
      {children}
    </tr>
  )
}

export function Cell({ children, mono, className = '' }) {
  return (
    <td className={`px-4 py-3 align-middle ${mono ? 'font-mono text-xs' : ''} ${className}`}
      style={{ color: 'var(--text)' }}>
      {children ?? <span style={{ color: 'var(--text-faint)' }}>—</span>}
    </td>
  )
}
