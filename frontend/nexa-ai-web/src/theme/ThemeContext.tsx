/**
 * Theme state: light, dark, or follow the operating system.
 *
 * <p>Driven by Bootstrap's `data-bs-theme` attribute on `<html>`, which is what makes every
 * Bootstrap component follow the theme without a per-component override. The token layer in
 * `_tokens.scss` feeds it; this module only owns which value is set and persists it.
 *
 * <p>This is a **foundation**, not the finished visual system. It establishes the mechanism,
 * the persistence and the no-flash behaviour. The palette itself is Phase 10's business.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

export type ThemePreference = 'light' | 'dark' | 'system'

/** The two values `data-bs-theme` actually takes. */
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'nexa.theme'

interface ThemeContextValue {
  /** What the user chose, which may be 'system'. */
  readonly preference: ThemePreference
  /** What is actually applied right now, after resolving 'system'. */
  readonly resolved: ResolvedTheme
  readonly setPreference: (preference: ThemePreference) => void
  /** Cycles light -> dark -> system. Used by the single toggle in the header. */
  readonly cycle: () => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

/** The stored preference, validated. A corrupt value falls back to 'system'. */
function readStoredPreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') {
      return stored
    }
  } catch {
    // localStorage throws in private browsing modes on some browsers. Not worth failing over:
    // falling back to 'system' is a perfectly good outcome.
  }
  return 'system'
}

/** The operating system's preference, defaulting to light where it cannot be read. */
function systemTheme(): ResolvedTheme {
  if (typeof window.matchMedia !== 'function') {
    return 'light'
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function ThemeProvider({ children }: { readonly children: ReactNode }): React.JSX.Element {
  const [preference, setPreferenceState] = useState<ThemePreference>(readStoredPreference)
  const [resolved, setResolved] = useState<ResolvedTheme>(() =>
    preference === 'system' ? systemTheme() : preference,
  )

  useEffect(() => {
    const next = preference === 'system' ? systemTheme() : preference
    setResolved(next)
    document.documentElement.setAttribute('data-bs-theme', next)

    try {
      window.localStorage.setItem(STORAGE_KEY, preference)
    } catch {
      // Persistence is a nicety. The theme still applies for this session.
    }
  }, [preference])

  useEffect(() => {
    if (preference !== 'system') {
      return
    }

    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (): void => {
      const next = media.matches ? 'dark' : 'light'
      setResolved(next)
      document.documentElement.setAttribute('data-bs-theme', next)
    }

    media.addEventListener('change', onChange)
    return () => {
      media.removeEventListener('change', onChange)
    }
  }, [preference])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
  }, [])

  const cycle = useCallback(() => {
    setPreferenceState((current) => {
      // light -> dark -> system -> light. Landing on 'system' last means a user who never touches
      // the toggle keeps following their OS, and one who does can still get back to it.
      if (current === 'light') {
        return 'dark'
      }
      if (current === 'dark') {
        return 'system'
      }
      return 'light'
    })
  }, [])

  const value = useMemo<ThemeContextValue>(
    () => ({ preference, resolved, setPreference, cycle }),
    [preference, resolved, setPreference, cycle],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (context === null) {
    throw new Error('useTheme must be used inside a ThemeProvider')
  }
  return context
}
