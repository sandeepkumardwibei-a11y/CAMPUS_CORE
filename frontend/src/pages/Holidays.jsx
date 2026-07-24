import { useState } from 'react'
import { PartyPopper, ChevronLeft, ChevronRight } from 'lucide-react'
import { HOLIDAYS_2026, HOLIDAY_MAP } from '../lib/holidays'
import { PageHeader, Card } from '../components/ui'

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December']
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const YEAR = 2026

function pad(n) { return String(n).padStart(2, '0') }

function MonthGrid({ month }) {
  const first = new Date(YEAR, month, 1).getDay()
  const days = new Date(YEAR, month + 1, 0).getDate()
  const cells = []
  for (let i = 0; i < first; i++) cells.push(null)
  for (let d = 1; d <= days; d++) cells.push(d)

  return (
    <div className="animate-fade-up">
      <div className="grid grid-cols-7 gap-1 mb-2">
        {WEEKDAYS.map((w) => (
          <div key={w} className="text-center text-[11px] font-semibold py-1" style={{ color: 'var(--text-faint)' }}>{w}</div>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-1">
        {cells.map((d, i) => {
          if (!d) return <div key={i} />
          const key = `${YEAR}-${pad(month + 1)}-${pad(d)}`
          const h = HOLIDAY_MAP[key]
          const isWeekend = new Date(YEAR, month, d).getDay() % 6 === 0
          return (
            <div key={i}
              title={h ? h.name : ''}
              className={`relative aspect-square rounded-xl grid place-items-center text-sm transition
                ${h ? 'font-bold ring-1 ring-emerald-500/40 bg-emerald-500/10 hover:scale-105' : 'hover:bg-black/5 dark:hover:bg-white/5'}`}
              style={{ color: h ? 'var(--text)' : (isWeekend ? 'var(--text-faint)' : 'var(--text-muted)') }}>
              {h ? (
                <div className="flex flex-col items-center leading-none">
                  <span className="text-lg animate-bounce" style={{ animationDuration: '2.4s' }}>{h.icon}</span>
                  <span className="text-[10px] mt-0.5">{d}</span>
                </div>
              ) : d}
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default function Holidays() {
  const [month, setMonth] = useState(new Date().getMonth())

  const monthHolidays = HOLIDAYS_2026.filter((h) => Number(h.date.slice(5, 7)) === month + 1)

  return (
    <div>
      <PageHeader icon={PartyPopper} title="Holiday Calendar"
        subtitle={`Indian holidays for ${YEAR}. Exams cannot be scheduled on these dates.`} />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="p-5 lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <button onClick={() => setMonth((m) => (m + 11) % 12)}
              className="p-2 rounded-lg hover:bg-black/5 dark:hover:bg-white/5"><ChevronLeft size={18} /></button>
            <h3 className="font-display font-bold text-lg" style={{ color: 'var(--text)' }}>{MONTHS[month]} {YEAR}</h3>
            <button onClick={() => setMonth((m) => (m + 1) % 12)}
              className="p-2 rounded-lg hover:bg-black/5 dark:hover:bg-white/5"><ChevronRight size={18} /></button>
          </div>
          <MonthGrid key={month} month={month} />
        </Card>

        <Card className="p-5 h-fit">
          <h3 className="font-display font-semibold mb-3" style={{ color: 'var(--text)' }}>
            {monthHolidays.length ? `Holidays in ${MONTHS[month]}` : `No holidays in ${MONTHS[month]}`}
          </h3>
          <div className="space-y-2">
            {monthHolidays.map((h) => (
              <div key={h.date} className="flex items-center gap-3 p-2.5 rounded-xl bg-emerald-500/5 border border-emerald-500/20 animate-fade-up">
                <span className="text-2xl">{h.icon}</span>
                <div>
                  <p className="text-sm font-medium" style={{ color: 'var(--text)' }}>{h.name}</p>
                  <p className="text-[11px]" style={{ color: 'var(--text-faint)' }}>
                    {new Date(h.date).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <Card className="p-5 mt-6">
        <h3 className="font-display font-semibold mb-3" style={{ color: 'var(--text)' }}>All {YEAR} holidays</h3>
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {HOLIDAYS_2026.map((h) => (
            <div key={h.date} className="flex items-center gap-3 p-2.5 rounded-xl hover:bg-black/5 dark:hover:bg-white/5 transition">
              <span className="text-xl">{h.icon}</span>
              <div>
                <p className="text-sm font-medium" style={{ color: 'var(--text)' }}>{h.name}</p>
                <p className="text-[11px]" style={{ color: 'var(--text-faint)' }}>
                  {new Date(h.date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                </p>
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  )
}
