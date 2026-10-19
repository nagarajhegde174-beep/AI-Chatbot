import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../../context/ThemeContext.tsx'

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()

  return (
    <button
      type="button"
      className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
      onClick={toggleTheme}
      title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
      aria-label="Toggle theme"
    >
      {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
      <span className="text-capitalize small d-none d-sm-inline">{theme}</span>
    </button>
  )
}
