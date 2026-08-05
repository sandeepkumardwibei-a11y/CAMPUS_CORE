// Lightweight, dependency-free chart components rendered with inline SVG.
// The project has no charting library installed, so these keep the bundle small
// and match the app's CSS-variable theming (works in light & dark modes).

// ---- PieChart ---------------------------------------------------------------
// data: [{ label, value, color }]  — renders a donut with a centered total and a legend.
export function PieChart({ data = [], size = 180, title }) {
  const items = data.filter((d) => Number(d.value) > 0)
  const total = items.reduce((s, d) => s + Number(d.value), 0)

  if (!total) {
    return (
      <div className="flex flex-col items-center justify-center py-8">
        {title && <p className="text-sm font-semibold mb-2" style={{ color: 'var(--text)' }}>{title}</p>}
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>No data to chart yet.</p>
      </div>
    )
  }

  const radius = size / 2
  const stroke = size * 0.22
  const r = radius - stroke / 2
  const circumference = 2 * Math.PI * r
  let offset = 0

  return (
    <div className="flex flex-col items-center">
      {title && <p className="text-sm font-semibold mb-3" style={{ color: 'var(--text)' }}>{title}</p>}
      <div className="flex items-center gap-6 flex-wrap justify-center">
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
          <g transform={`rotate(-90 ${radius} ${radius})`}>
            {/* track */}
            <circle cx={radius} cy={radius} r={r} fill="none" stroke="var(--border)" strokeWidth={stroke} opacity="0.4" />
            {items.map((d, i) => {
              const frac = Number(d.value) / total
              const dash = frac * circumference
              const seg = (
                <circle
                  key={i}
                  cx={radius}
                  cy={radius}
                  r={r}
                  fill="none"
                  stroke={d.color}
                  strokeWidth={stroke}
                  strokeDasharray={`${dash} ${circumference - dash}`}
                  strokeDashoffset={-offset}
                  strokeLinecap="butt"
                />
              )
              offset += dash
              return seg
            })}
          </g>
          <text x="50%" y="47%" textAnchor="middle" fontSize={size * 0.2} fontWeight="700" fill="var(--text)">{total}</text>
          <text x="50%" y="62%" textAnchor="middle" fontSize={size * 0.08} fill="var(--text-muted)">total</text>
        </svg>

        <div className="space-y-2">
          {items.map((d, i) => {
            const pct = ((Number(d.value) / total) * 100).toFixed(1)
            return (
              <div key={i} className="flex items-center gap-2 text-sm">
                <span className="inline-block w-3 h-3 rounded-sm shrink-0" style={{ background: d.color }} />
                <span style={{ color: 'var(--text)' }}>{d.label}</span>
                <span style={{ color: 'var(--text-muted)' }}>· {d.value} ({pct}%)</span>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

// ---- StackedBarChart --------------------------------------------------------
// data: [{ label, segments: [{ label, value, color }] }]
// Each bar is normalized to 100% and split into colored segments — useful for
// showing the Present / Late / Official duty / Absent mix per month.
export function StackedBarChart({ data = [], height = 200, title }) {
  const items = (data || []).filter((d) => (d.segments || []).some((s) => Number(s.value) > 0))
  if (!items.length) {
    return (
      <div className="flex flex-col items-center justify-center py-8">
        {title && <p className="text-sm font-semibold mb-2" style={{ color: 'var(--text)' }}>{title}</p>}
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>No data to chart yet.</p>
      </div>
    )
  }

  const barH = height - 34
  // Legend = union of all segment labels/colors across the dataset.
  const legend = []
  const seen = new Set()
  items.forEach((d) => (d.segments || []).forEach((s) => {
    if (!seen.has(s.label)) { seen.add(s.label); legend.push({ label: s.label, color: s.color }) }
  }))

  return (
    <div>
      {title && <p className="text-sm font-semibold mb-3" style={{ color: 'var(--text)' }}>{title}</p>}
      <div className="flex items-end gap-3 overflow-x-auto pb-1" style={{ height }}>
        {items.map((d, i) => {
          const total = (d.segments || []).reduce((s, x) => s + (Number(x.value) || 0), 0)
          return (
            <div key={i} className="flex flex-col items-center justify-end min-w-12 flex-1" style={{ height: barH + 24 }}>
              <div className="w-full rounded-md overflow-hidden flex flex-col-reverse" style={{ height: barH }} title={d.label}>
                {(d.segments || []).map((s, j) => {
                  const v = Number(s.value) || 0
                  const pct = total > 0 ? (v / total) * 100 : 0
                  if (pct <= 0) return null
                  return (
                    <div
                      key={j}
                      style={{ height: `${pct}%`, background: s.color }}
                      title={`${s.label}: ${Math.round(pct)}%`}
                    />
                  )
                })}
              </div>
              <span className="text-xs mt-2 text-center" style={{ color: 'var(--text-muted)' }}>{d.label}</span>
            </div>
          )
        })}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 mt-3 justify-center">
        {legend.map((l, i) => (
          <div key={i} className="flex items-center gap-1.5 text-xs">
            <span className="inline-block w-3 h-3 rounded-sm" style={{ background: l.color }} />
            <span style={{ color: 'var(--text-muted)' }}>{l.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

// ---- BarChart ---------------------------------------------------------------
// data: [{ label, value }] — value is a percentage (0–100). Renders vertical bars.
export function BarChart({ data = [], height = 200, title, unit = '%', max = 100, color = '#10b981' }) {
  const items = data || []
  if (!items.length) {
    return (
      <div className="flex flex-col items-center justify-center py-8">
        {title && <p className="text-sm font-semibold mb-2" style={{ color: 'var(--text)' }}>{title}</p>}
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>No data to chart yet.</p>
      </div>
    )
  }

  const barH = height - 34 // leave room for the x-axis labels
  const ceiling = Math.max(max, ...items.map((d) => Number(d.value) || 0))

  return (
    <div>
      {title && <p className="text-sm font-semibold mb-3" style={{ color: 'var(--text)' }}>{title}</p>}
      <div className="flex items-end gap-3 overflow-x-auto pb-1" style={{ height }}>
        {items.map((d, i) => {
          const v = Number(d.value) || 0
          const h = ceiling > 0 ? Math.round((v / ceiling) * barH) : 0
          // color shortage months (<75%) rose, healthy emerald
          const barColor = v < 75 ? '#f43f5e' : color
          return (
            <div key={i} className="flex flex-col items-center justify-end min-w-12 flex-1" style={{ height: barH + 24 }}>
              <span className="text-xs mb-1 font-semibold" style={{ color: 'var(--text)' }}>{v}{unit}</span>
              <div
                className="w-full rounded-t-md transition-all"
                style={{ height: `${h}px`, background: barColor, minHeight: v > 0 ? 4 : 0 }}
                title={`${d.label}: ${v}${unit}`}
              />
              <span className="text-xs mt-2 text-center" style={{ color: 'var(--text-muted)' }}>{d.label}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
