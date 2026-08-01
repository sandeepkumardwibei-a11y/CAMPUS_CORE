import { Sun, Moon, Monitor } from 'lucide-react'
import { useTheme } from '../context/ThemeContext'

const cfg = {
  light: { icon: Sun, next: 'Switch to dark' },
  dark: { icon: Moon, next: 'Switch to system' },
  system: { icon: Monitor, next: 'Switch to light' },
}

// `variant="overlay"` is for pages like Landing/Login/Register where the toggle floats
// on top of a fixed dark image/gradient backdrop that never changes with the theme.
// var(--text) goes near-black in light mode, which made the icon invisible against
// that permanently-dark backdrop — so overlay mode forces light-on-dark styling
// instead, making the button visible regardless of the active theme.
export default function ThemeToggle({ variant = 'default' }) {
  const { mode, cycle } = useTheme()
  const { icon: Icon, next } = cfg[mode]

  if (variant === 'overlay') {
    return (
      <button onClick={cycle} title={next} aria-label={`Theme: ${mode}. ${next}`}
        className="w-10 h-10 rounded-xl grid place-items-center border backdrop-blur-md bg-white/10 border-white/25 text-white hover:bg-white/20 transition">
        <Icon size={18} />
      </button>
    )
  }

  return (
    <button onClick={cycle} title={next} aria-label={`Theme: ${mode}. ${next}`}
      className="w-10 h-10 rounded-xl grid place-items-center border hover:bg-black/5 dark:hover:bg-white/5 transition"
      style={{ borderColor: 'var(--border-strong)', color: 'var(--text)' }}>
      <Icon size={18} />
    </button>
  )
}
