import { Sun, Moon, Monitor } from 'lucide-react'
import { useTheme } from '../context/ThemeContext'

const cfg = {
  light: { icon: Sun, next: 'Switch to dark' },
  dark: { icon: Moon, next: 'Switch to system' },
  system: { icon: Monitor, next: 'Switch to light' },
}

export default function ThemeToggle() {
  const { mode, cycle } = useTheme()
  const { icon: Icon, next } = cfg[mode]
  return (
    <button onClick={cycle} title={next} aria-label={`Theme: ${mode}. ${next}`}
      className="w-10 h-10 rounded-xl grid place-items-center border hover:bg-black/5 dark:hover:bg-white/5 transition"
      style={{ borderColor: 'var(--border-strong)', color: 'var(--text)' }}>
      <Icon size={18} />
    </button>
  )
}
