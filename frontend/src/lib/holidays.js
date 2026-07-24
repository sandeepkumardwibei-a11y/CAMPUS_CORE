// Hardcoded Indian holidays for 2026 (item 8).
// `icon` maps to an emoji so each holiday shows a themed symbol
// (e.g. Christmas -> tree). Keep in sync with backend HolidayCalendar.java.
export const HOLIDAYS_2026 = [
  { date: '2026-01-01', name: "New Year's Day",           icon: '🎉' },
  { date: '2026-01-14', name: 'Makar Sankranti / Pongal', icon: '🪁' },
  { date: '2026-01-26', name: 'Republic Day',             icon: '🇮🇳' },
  { date: '2026-02-15', name: 'Maha Shivaratri',          icon: '🕉️' },
  { date: '2026-03-04', name: 'Holi',                     icon: '🎨' },
  { date: '2026-03-21', name: 'Eid-ul-Fitr',              icon: '🌙' },
  { date: '2026-03-27', name: 'Ram Navami',               icon: '🏹' },
  { date: '2026-04-03', name: 'Good Friday',              icon: '✝️' },
  { date: '2026-04-14', name: 'Ambedkar Jayanti',         icon: '📜' },
  { date: '2026-05-01', name: 'May Day',                  icon: '⚒️' },
  { date: '2026-05-27', name: 'Eid-ul-Adha (Bakrid)',     icon: '🐑' },
  { date: '2026-08-15', name: 'Independence Day',         icon: '🇮🇳' },
  { date: '2026-08-26', name: 'Janmashtami',              icon: '🪈' },
  { date: '2026-09-14', name: 'Ganesh Chaturthi',         icon: '🐘' },
  { date: '2026-10-02', name: 'Gandhi Jayanti',           icon: '🕊️' },
  { date: '2026-10-20', name: 'Dussehra (Vijayadashami)', icon: '🏹' },
  { date: '2026-11-08', name: 'Diwali (Deepavali)',       icon: '🪔' },
  { date: '2026-11-24', name: 'Guru Nanak Jayanti',       icon: '🪯' },
  { date: '2026-12-25', name: 'Christmas',                icon: '🎄' },
]

// Quick lookup: 'YYYY-MM-DD' -> holiday object
export const HOLIDAY_MAP = HOLIDAYS_2026.reduce((m, h) => { m[h.date] = h; return m }, {})

export function isHoliday(dateStr) {
  return !!HOLIDAY_MAP[dateStr]
}
