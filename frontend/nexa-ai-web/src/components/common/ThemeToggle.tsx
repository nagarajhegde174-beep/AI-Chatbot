import { Moon, Sun, Laptop } from 'lucide-react'
import { useTheme } from '../../context/ThemeContext.tsx'

export function ThemeToggle({ variant = 'icon' }: { readonly variant?: 'icon' | 'select' }) {
  const { theme, setTheme, toggleTheme } = useTheme()

  if (variant === 'select') {
    return (
      <div className="btn-group btn-group-sm" role="group" aria-label="Theme selection">
        <button
          type="button"
          className={`btn ${theme === 'light' ? 'btn-primary' : 'btn-outline-secondary'}`}
          onClick={() => setTheme('light')}
          aria-pressed={theme === 'light'}
        >
          <Sun size={14} className="me-1" /> Light
        </button>
        <button
          type="button"
          className={`btn ${theme === 'dark' ? 'btn-primary' : 'btn-outline-secondary'}`}
          onClick={() => setTheme('dark')}
          aria-pressed={theme === 'dark'}
        >
          <Moon size={14} className="me-1" /> Dark
        </button>
        <button
          type="button"
          className={`btn ${theme === 'system' ? 'btn-primary' : 'btn-outline-secondary'}`}
          onClick={() => setTheme('system')}
          aria-pressed={theme === 'system'}
        >
          <Laptop size={14} className="me-1" /> System
        </button>
      </div>
    )
  }

  return (
    <button
      type="button"
      className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1.5 nexa-touch-target"
      onClick={toggleTheme}
      title={`Current: ${theme}. Click to switch theme.`}
      aria-label={`Current theme: ${theme}. Click to toggle.`}
    >
      {theme === 'light' && <Sun size={15} />}
      {theme === 'dark' && <Moon size={15} />}
      {theme === 'system' && <Laptop size={15} />}
      <span className="text-capitalize small d-none d-md-inline" style={{ fontSize: '0.8rem' }}>
        {theme}
      </span>
    </button>
  )
}
