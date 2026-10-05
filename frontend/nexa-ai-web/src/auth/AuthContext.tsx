/**
 * Authentication state for the whole application.
 *
 * <p>One provider rather than per-component fetching, so the router can make a single decision
 * about protected routes instead of every screen deciding for itself and disagreeing.
 *
 * <p><strong>There is no token in this context.</strong> The credential is an HTTP-only cookie
 * this code cannot read. The context holds only the display facts a UI needs — whether a session
 * exists, and the email it belongs to. Keeping a token here would be the single most damaging
 * thing this frontend could do.
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

import * as authApi from '../api/auth'
import { toApiError, type ApiError } from '../api/client'

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  readonly status: AuthStatus
  readonly email: string | null
  readonly isAdmin: boolean
  readonly signIn: (email: string, password: string) => Promise<void>
  readonly signUp: (email: string, password: string, displayName: string) => Promise<void>
  readonly signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { readonly children: ReactNode }): React.JSX.Element {
  // 'checking' rather than a boolean. A tri-state is what lets the router avoid flashing the
  // sign-in page at a signed-in user on every refresh: there is a third answer, "ask the server",
  // and collapsing it into false is what causes that flash.
  const [status, setStatus] = useState<AuthStatus>('checking')
  const [email, setEmail] = useState<string | null>(null)
  const [roles, setRoles] = useState<readonly string[]>([])

  useEffect(() => {
    let cancelled = false

    void authApi
      .isAuthenticated()
      .then((signedIn) => {
        if (cancelled) {
          return
        }
        setStatus(signedIn ? 'authenticated' : 'anonymous')
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('anonymous')
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (emailAddress: string, password: string) => {
    const result = await authApi.login({ email: emailAddress, password })
    setEmail(result.email)
    setRoles(result.roles)
    setStatus('authenticated')
  }, [])

  const signUp = useCallback(async (emailAddress: string, password: string, displayName: string) => {
    const result = await authApi.register({ email: emailAddress, password, displayName })
    setEmail(result.email)
    setRoles(result.roles)
    setStatus('authenticated')
  }, [])

  const signOut = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      // Cleared even if the request failed. Leaving a stale "signed in" on screen after the user
      // asked to sign out is worse than a cookie the server keeps.
      setEmail(null)
      setRoles([])
      setStatus('anonymous')
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      email,
      isAdmin: roles.includes('ADMIN'),
      signIn,
      signUp,
      signOut,
    }),
    [status, email, roles, signIn, signUp, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/**
 * Reads the auth context.
 *
 * @throws when used outside the provider. Failing loudly beats `undefined` checks scattered
 *         through every component that consumes it.
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}

/** Formats an unknown thrown value for display. */
export function describeError(cause: unknown): string {
  return toApiError(cause as ApiError).displayMessage
}
