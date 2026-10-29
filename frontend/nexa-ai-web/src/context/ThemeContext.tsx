import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Theme } from '../types/index.ts'

interface ThemeContextValue {
  readonly theme: Theme
  readonly setTheme: (theme: Theme) => void
  readonly toggleTheme: () => void
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined)

export function ThemeProvider({ children }: { readonly children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    const saved = localStorage.getItem('nexa_theme')
    if (saved === 'light' || saved === 'dark' || saved === 'system') {
      return saved as Theme
    }
    return 'system'
  })

  useEffect(() => {
    const applyTheme = (t: Theme) => {
      document.documentElement.setAttribute('data-theme', t)
      let resolved: 'light' | 'dark' = 'dark'
      if (t === 'light') {
        resolved = 'light'
      } else if (t === 'dark') {
        resolved = 'dark'
      } else {
        resolved = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
      }
      document.documentElement.setAttribute('data-bs-theme', resolved)
    }

    applyTheme(theme)
    localStorage.setItem('nexa_theme', theme)

    if (theme === 'system') {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
      const listener = (e: MediaQueryListEvent) => {
        document.documentElement.setAttribute('data-bs-theme', e.matches ? 'dark' : 'light')
      }
      mediaQuery.addEventListener('change', listener)
      return () => mediaQuery.removeEventListener('change', listener)
    }
  }, [theme])

  const setTheme = (nextTheme: Theme) => {
    setThemeState(nextTheme)
  }

  const toggleTheme = () => {
    setThemeState((prev) => {
      if (prev === 'light') return 'dark'
      if (prev === 'dark') return 'system'
      return 'light'
    })
  }

  return (
    <ThemeContext.Provider value={{ theme, setTheme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider')
  return ctx
}
