import { Check } from 'lucide-react'

export function Stepper({ steps, current }) {
  const idx = steps.indexOf(current)
  return (
    <div className="flex items-center overflow-x-auto pb-2">
      {steps.map((s, i) => {
        const done = idx > i
        const active = idx === i
        return (
          <div key={s} className="flex items-center shrink-0">
            <div className="flex flex-col items-center gap-1.5 w-[92px] text-center">
              <div className={`w-8 h-8 rounded-full grid place-items-center text-xs font-bold ring-2 transition
                ${done ? 'gradient-btn text-white ring-transparent'
                  : active ? 'bg-emerald-500/15 text-emerald-400 ring-emerald-500'
                  : 'ring-transparent'}`}
                style={!done && !active ? { background: 'var(--surface-solid)', color: 'var(--text-faint)', boxShadow: 'inset 0 0 0 2px var(--border-strong)' } : undefined}>
                {done ? <Check size={15} /> : i + 1}
              </div>
              <span className={`text-[10px] leading-tight font-medium ${active ? 'text-emerald-400' : ''}`}
                style={!active ? { color: 'var(--text-faint)' } : undefined}>
                {s.replace(/_/g, ' ')}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div className={`h-0.5 w-6 -mt-5 ${done ? 'bg-emerald-500' : ''}`}
                style={!done ? { background: 'var(--border-strong)' } : undefined} />
            )}
          </div>
        )
      })}
    </div>
  )
}

export function StatCard({ label, value, icon: Icon, tone = 'indigo', sub }) {
  const tones = {
    indigo: 'from-emerald-500/20 to-green-500/10 text-emerald-400',
    emerald: 'from-emerald-500/20 to-teal-500/10 text-emerald-400',
    amber: 'from-amber-500/20 to-orange-500/10 text-amber-400',
    fuchsia: 'from-green-500/20 to-lime-500/10 text-green-400',
  }
  return (
    <div className="glass rounded-2xl p-5 flex items-center gap-4 animate-fade-up">
      <div className={`w-12 h-12 rounded-xl grid place-items-center bg-gradient-to-br ${tones[tone]}`}>
        {Icon && <Icon size={22} />}
      </div>
      <div className="min-w-0">
        <p className="text-2xl font-bold font-display leading-none" style={{ color: 'var(--text)' }}>{value}</p>
        <p className="text-xs mt-1.5 truncate" style={{ color: 'var(--text-muted)' }}>{label}</p>
        {sub && <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-faint)' }}>{sub}</p>}
      </div>
    </div>
  )
}

export function Tabs({ tabs, active, onChange }) {
  return (
    <div className="flex gap-1 p-1 rounded-xl glass w-fit max-w-full overflow-x-auto mb-5">
      {tabs.map((t) => (
        <button key={t.key} onClick={() => onChange(t.key)}
          className={`px-3.5 py-1.5 rounded-lg text-sm font-medium whitespace-nowrap transition
            ${active === t.key ? 'gradient-btn text-white shadow' : 'hover:bg-black/5 dark:hover:bg-white/5'}`}
          style={active !== t.key ? { color: 'var(--text-muted)' } : undefined}>
          {t.label}
        </button>
      ))}
    </div>
  )
}
